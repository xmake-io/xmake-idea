package org.tboox.xmake.service.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.tboox.xmake.shared.build.XMakeBuildTargetType;

import java.util.*;

public class XMakeBuilderService extends BuilderService {

    @NotNull
    public List<? extends BuildTargetType<?>> getTargetTypes() {
        return Arrays.asList(XMakeBuildTargetType.INSTANCE);
    }

    @NotNull
    public List<? extends TargetBuilder<?, ?>> createBuilders() {
        return Arrays.asList(new XMakeBuilder());
    }
}
