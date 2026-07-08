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

import com.intellij.openapi.project.Project;
import com.intellij.tools.Tool;
import com.intellij.tools.ToolsGroup;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildManager;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildTarget;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionExternalToolConfiguration;
import com.jetbrains.cidr.cpp.execution.external.build.CLionProjectToolManager;

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
        ToolsGroup<Tool> group = new ToolsGroup<>(toolGroup);
        List<CLionExternalBuildTarget> result = new ArrayList<>();
        for (CustomBuildTargetsIntegration.TargetSpec target : targets) {
            Tool buildTool = newTool("xmake build " + target.getName(), toolGroup, xmakeBinary, workingDir, target.getBuildArgs());
            Tool cleanTool = newTool("xmake clean " + target.getName(), toolGroup, xmakeBinary, workingDir, target.getCleanArgs());
            group.addElement(buildTool);
            group.addElement(cleanTool);

            // getTool() resolves the Tool from CLionProjectToolManager by matching getActionId().
            CLionExternalToolConfiguration buildCfg = new CLionExternalToolConfiguration(buildTool.getActionId(), project);
            CLionExternalToolConfiguration cleanCfg = new CLionExternalToolConfiguration(cleanTool.getActionId(), project);
            CLionExternalBuildConfiguration configuration = new CLionExternalBuildConfiguration(
                    target.getName(), buildCfg, cleanCfg, /* toolchainName = */ "", UUID.randomUUID());
            result.add(new CLionExternalBuildTarget(
                    target.getName(),
                    projectName,
                    Collections.singletonList(configuration),
                    CLionExternalConfiguration.Type.Tool.INSTANCE,
                    UUID.randomUUID()));
        }

        // Tools must be registered before the targets resolve them via getActionId().
        CLionProjectToolManager.getInstance(project).setTools(Collections.singletonList(group));
        CLionExternalBuildManager.getInstance(project).setTargets(result);
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
