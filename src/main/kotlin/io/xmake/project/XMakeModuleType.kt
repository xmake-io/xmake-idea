package io.xmake.project

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import io.xmake.icons.XMakeIcons

import javax.swing.*

class XMakeModuleType : ModuleType<XMakeModuleBuilder>(MODULE_TYPE) {

    override fun createModuleBuilder(): XMakeModuleBuilder {
        return XMakeModuleBuilder()
    }

    override fun getName(): String {
        return "XMake"
    }

    override fun getDescription(): String {
        return "XMake Module"
    }

    override fun getNodeIcon(b: Boolean): Icon {
        return XMakeIcons.XMAKE
    }

    override fun isMarkInnerSupportedFor(type: JpsModuleSourceRootType<*>?): Boolean {
        return true
    }

    companion object {

        // the module type name
        private val MODULE_TYPE = "XMake.Module"

        // the instance
        val instance: XMakeModuleType
            get() = ModuleTypeManager.getInstance().findByID(MODULE_TYPE) as XMakeModuleType
    }
}
