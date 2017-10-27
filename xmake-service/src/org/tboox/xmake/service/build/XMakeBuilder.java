package org.tboox.xmake.service.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.tboox.xmake.shared.build.XMakeBuildTarget;
import org.tboox.xmake.shared.build.XMakeBuildTargetType;
import java.io.IOException;
import java.util.*;

public class XMakeBuilder extends TargetBuilder<BuildRootDescriptor, XMakeBuildTarget> {

    protected XMakeBuilder() {
        super(Collections.singletonList(XMakeBuildTargetType.INSTANCE));
    }

    @Override
    @NotNull
    public String getPresentableName() {
        return "XMake Builder";
    }

    @Override
    public void buildStarted(final CompileContext context) {
        super.buildStarted(context);
    }

    @Override
    public void buildFinished(final CompileContext context) {
        super.buildFinished(context);
    }

    @Override
    public void build(@NotNull final XMakeBuildTarget buildTarget,
                      @NotNull final DirtyFilesHolder<BuildRootDescriptor, XMakeBuildTarget> holder,
                      @NotNull final BuildOutputConsumer outputConsumer,
                      @NotNull final CompileContext context) throws ProjectBuildException, IOException {


    }
}
