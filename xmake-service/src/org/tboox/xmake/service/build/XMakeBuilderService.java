package org.tboox.xmake.service.build;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.tboox.xmake.shared.build.XMakeBuildTargetType;

import java.util.*;

public class XMakeBuilderService extends BuilderService {

    // the logger
    private static final Logger Log = Logger.getInstance(XMakeBuilderService.class.getName());

    @NotNull
    public List<? extends BuildTargetType<?>> getTargetTypes() {
        Log.info("getTargetTypes");
        return Arrays.asList(XMakeBuildTargetType.INSTANCE);
    }

    @NotNull
    public List<? extends TargetBuilder<?, ?>> createBuilders() {
        Log.info("createBuilders");
        return Arrays.asList(new XMakeBuilder());
    }
}
