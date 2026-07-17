package io.xmake.debug

import io.xmake.utils.Logger

/** Resolves modules packaged in lib/modules, which use class loaders separate from the root plugin. */
internal object ContentModuleClassLoaderResolver {

    private const val TAG = "ContentModuleClassLoaderResolver"

    fun resolve(rootDescriptor: Any, moduleName: String): ClassLoader? {
        return try {
            val contentModules = rootDescriptor.javaClass
                .getMethod("getContentModules")
                .invoke(rootDescriptor) as? Iterable<*> ?: return null
            val moduleDescriptor = contentModules.firstOrNull { descriptor ->
                descriptor != null && descriptor.javaClass
                    .getMethod("getModuleNameString")
                    .invoke(descriptor) == moduleName
            } ?: return null

            moduleDescriptor.javaClass
                .getMethod("getPluginClassLoader")
                .invoke(moduleDescriptor) as? ClassLoader
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to resolve content module class loader: ${e.message}")
            null
        } catch (e: LinkageError) {
            Logger.d(TAG, "Content module descriptor is not linkable: ${e.message}")
            null
        }
    }
}
