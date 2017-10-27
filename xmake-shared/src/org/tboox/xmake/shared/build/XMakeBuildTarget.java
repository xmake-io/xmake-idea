package org.tboox.xmake.shared.build;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import java.io.File;
import java.util.*;

public class XMakeBuildTarget extends BuildTarget<BuildRootDescriptor> {

    // the logger
    private static final Logger Log = Logger.getInstance(XMakeBuildTarget.class.getName());

    private XMakeBuildTarget() {
        super(XMakeBuildTargetType.INSTANCE);
    }

    @NotNull
    public String getId() {
        return "target.getId";
    }

    @NotNull
    public Collection<File> getOutputRoots(CompileContext context) {
        return Collections.singleton(new File("/tmp/xmake/XMakeBuildTarget.OutputRoots"));
    }

    @NotNull
    public String getPresentableName() {
        return "XMakeBuildTarget.PresentableName";
    }

    public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
        final ArrayList<BuildTarget<?>> result = new ArrayList<>();
        Log.info("computeDependencies");
        return result;
    }

    @NotNull
    public List<BuildRootDescriptor> computeRootDescriptors(final JpsModel model,
                                                            final ModuleExcludeIndex index,
                                                            final IgnoredFileIndex ignoredFileIndex,
                                                            final BuildDataPaths dataPaths) {
        final List<BuildRootDescriptor> result = new ArrayList<>();
        Log.info("computeRootDescriptors");
        return result;
    }

    @Nullable
    public BuildRootDescriptor findRootDescriptor(final String rootId, final BuildRootIndex rootIndex) {

        Log.info("findRootDescriptor");
        for (BuildRootDescriptor descriptor : rootIndex.getTargetRoots(this, null)) {
            if (descriptor.getRootId().equals(rootId)) {
                return descriptor;
            }
        }

        return null;
    }
}
