/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        CLionBuildTargetRegistrar.java
 *
 */
package io.xmake.debug.clion;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolsGroup;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildManager;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildTarget;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalToolConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionProjectToolManager;
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.execution.BuildTargetAndConfigurationData;
import com.jetbrains.cidr.execution.ExecutableData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Installs xmake targets into CLion's Custom Build Targets subsystem. Written in Java because
 * {@code CLionProjectToolManager} (and some {@code CLionExternalBuildManager} members) are Kotlin
 * {@code internal} — forbidden cross-module for the Kotlin compiler, but freely callable from Java.
 * Called only on the EDT from {@link CustomBuildTargetsIntegration}.
 */
final class CLionBuildTargetRegistrar {

    private CLionBuildTargetRegistrar() {
    }

    static void register(Project project,
                         String xmakeBinary,
                         String workingDir,
                         String projectName,
                         String toolGroup,
                         List<CustomBuildTargetsIntegration.TargetSpec> targets) {
        // Bind the build/run to CLion's default toolchain; an empty toolchain makes CLion refuse to
        // start the configuration ("no toolchain").
        CPPToolchains.Toolchain defaultToolchain = CPPToolchains.getInstance().getDefaultToolchain();
        String toolchainName = defaultToolchain != null ? defaultToolchain.getName() : "";

        ToolsGroup<Tool> group = new ToolsGroup<>(toolGroup);
        List<CLionExternalBuildTarget> result = new ArrayList<>();
        // Keep the (build target, build config, spec) triples so we can create run configs afterwards.
        List<CLionExternalBuildTarget> builtTargets = new ArrayList<>();
        List<CLionExternalBuildConfiguration> builtConfigs = new ArrayList<>();
        for (CustomBuildTargetsIntegration.TargetSpec target : targets) {
            Tool buildTool = newTool("xmake build " + target.getName(), toolGroup, xmakeBinary, workingDir, target.getBuildArgs());
            Tool cleanTool = newTool("xmake clean " + target.getName(), toolGroup, xmakeBinary, workingDir, target.getCleanArgs());
            group.addElement(buildTool);
            group.addElement(cleanTool);

            // getTool() resolves the Tool from CLionProjectToolManager by matching getActionId().
            CLionExternalToolConfiguration buildCfg = new CLionExternalToolConfiguration(buildTool.getActionId(), project);
            CLionExternalToolConfiguration cleanCfg = new CLionExternalToolConfiguration(cleanTool.getActionId(), project);
            CLionExternalBuildConfiguration configuration = new CLionExternalBuildConfiguration(
                    target.getName(), buildCfg, cleanCfg, toolchainName, UUID.randomUUID());
            CLionExternalBuildTarget buildTarget = new CLionExternalBuildTarget(
                    target.getName(),
                    projectName,
                    Collections.singletonList(configuration),
                    CLionExternalConfiguration.Type.Tool.INSTANCE,
                    UUID.randomUUID());
            result.add(buildTarget);
            builtTargets.add(buildTarget);
            builtConfigs.add(configuration);
        }

        // Tools must be registered before the targets resolve them via getActionId().
        CLionProjectToolManager.getInstance(project).setTools(Collections.singletonList(group));
        CLionExternalBuildManager.getInstance(project).setTargets(result);

        // Create a ready-to-run "Xmake Executable" run config per executable target (with the
        // executable pre-filled), so the user gets working Run/Debug/Build without any manual setup.
        for (int i = 0; i < targets.size(); i++) {
            CustomBuildTargetsIntegration.TargetSpec target = targets.get(i);
            String exePath = target.getExecutablePath();
            if (!target.isExecutable() || exePath == null) {
                continue;
            }
            ensureRunConfiguration(project, target.getName(), builtTargets.get(i), builtConfigs.get(i), exePath);
        }
    }

    /**
     * Create — or refresh — the persistent "Xmake Executable" run config for [buildTarget].
     *
     * If a config of this name already exists we re-bind its target/configuration and rewrite its
     * executable path rather than skipping it: xmake encodes the build mode in the output path
     * ({@code build/<plat>/<arch>/<mode>/...}), so after a mode switch (reconfigure) the previously
     * baked path points at a stale binary from the old mode — e.g. a {@code release} binary with no
     * debug symbols, which is why the native debugger reports "No symbol table is loaded". Both the
     * binding and the exe path are plugin-managed (mode-derived); user edits to args/env/cwd survive.
     */
    private static void ensureRunConfiguration(Project project,
                                               String name,
                                               CLionExternalBuildTarget buildTarget,
                                               CLionExternalBuildConfiguration buildConfig,
                                               String exePath) {
        ConfigurationType type =
                ConfigurationTypeUtil.findConfigurationType(XMakeExecutableRunConfigurationType.class);
        if (type == null || type.getConfigurationFactories().length == 0) {
            return;
        }
        RunManager runManager = RunManager.getInstance(project);
        for (RunnerAndConfigurationSettings existing : runManager.getAllSettings()) {
            if (existing.getType() != null
                    && XMakeExecutableRunConfigurationType.ID.equals(existing.getType().getId())
                    && name.equals(existing.getName())) {
                bind(existing.getConfiguration(), buildTarget, buildConfig, exePath);
                return; // refreshed in place — don't duplicate
            }
        }
        ConfigurationFactory factory = type.getConfigurationFactories()[0];
        RunnerAndConfigurationSettings settings = runManager.createConfiguration(name, factory);
        bind(settings.getConfiguration(), buildTarget, buildConfig, exePath);
        runManager.addConfiguration(settings);
    }

    /** Point a run config at the given build target + freshly-resolved (mode-specific) executable. */
    private static void bind(RunConfiguration configuration,
                             CLionExternalBuildTarget buildTarget,
                             CLionExternalBuildConfiguration buildConfig,
                             String exePath) {
        if (configuration instanceof CLionExternalRunConfiguration) {
            CLionExternalRunConfiguration runConfig = (CLionExternalRunConfiguration) configuration;
            runConfig.setTargetAndConfigurationData(new BuildTargetAndConfigurationData(buildTarget, buildConfig));
            runConfig.setExecutableData(new ExecutableData(exePath));
        }
    }

    private static Tool newTool(String name, String group, String program, String workingDir, List<String> args) {
        Tool tool = new Tool();
        tool.setName(name);
        tool.setGroup(group);
        tool.setProgram(program);
        tool.setParameters(String.join(" ", args));
        tool.setWorkingDirectory(workingDir);
        tool.setEnabled(true);
        tool.setUseConsole(true);
        tool.setShowConsoleOnStdOut(true);
        tool.setShowConsoleOnStdErr(true);
        return tool;
    }
}
