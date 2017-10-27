package org.tboox.xmake.build;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import java.util.*;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

public class XMakeBuildTargetScopeProvider extends BuildTargetScopeProvider {

    // the logger
    private static final Logger Log = Logger.getInstance(XMakeBuildTargetScopeProvider.class.getName());

    // get build target scopes
    @NotNull
    public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull final CompileScope baseScope,
                                                           @NotNull final CompilerFilter filter,
                                                           @NotNull final Project project,
                                                           boolean forceBuild) {

        // trace
        Log.info("getBuildTargetScopes");

        // get target ids
        List<String> targetIds = new ArrayList<>();
        for (final Module module : baseScope.getAffectedModules()) {
            targetIds.add(module.getName());
        }

        // return empty list if no targets
        if (targetIds.isEmpty()) {
            return Collections.emptyList();
        }

        // return targets list
        return Collections.singletonList(CmdlineProtoUtil.createTargetsScope(XMakeBuildTargetType.INSTANCE.getTypeId(), targetIds, forceBuild));
    }
}
