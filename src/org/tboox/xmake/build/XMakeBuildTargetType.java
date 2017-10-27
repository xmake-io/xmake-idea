package org.tboox.xmake.build;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import java.util.*;

public class XMakeBuildTargetType extends BuildTargetType<XMakeBuildTarget> {

    // the logger
    private static final Logger Log = Logger.getInstance(XMakeBuildTargetType.class.getName());

    // the global instance
    public static final XMakeBuildTargetType INSTANCE = new XMakeBuildTargetType();

    // initializer
    private XMakeBuildTargetType() {
        super("xmake");
    }

    @NotNull
    public List<XMakeBuildTarget> computeAllTargets(@NotNull final JpsModel model) {
        final List<XMakeBuildTarget> result = new ArrayList<>();
        Log.info("computeAllTargets");
        return result;
    }

    // override create loader interface
    @NotNull
    public BuildTargetLoader<XMakeBuildTarget> createLoader(@NotNull final JpsModel model) {
        return new XMakeBuildTargetLoader(model);
    }

    // the xmake build target loader
    private static class XMakeBuildTargetLoader extends BuildTargetLoader<XMakeBuildTarget> {

        // my model
        private final JpsModel myModel;

        // initializer
        public XMakeBuildTargetLoader(final JpsModel model) {
            myModel = model;
        }

        // create target from the target id
        @Nullable
        public XMakeBuildTarget createTarget(@NotNull final String buildTargetId) {

            Log.info("createTarget: " + buildTargetId);
            return null;
        }
    }
}
