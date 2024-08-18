package io.xmake.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.*
import io.xmake.icons.XMakeIcons
import io.xmake.utils.SystemUtils
import org.jdom.Element
import javax.swing.Icon

@Deprecated("Please refer to the relevant content in folder io/xmake/project/toolkit.")
class XMakeSdkType : SdkType("XMake SDK") {

    override fun suggestHomePath(): String? {
        val program = SystemUtils.xmakeProgram
        if (program == "") {
            return null
        }
        return program
    }

    override fun isValidSdkHome(s: String): Boolean {
        return true
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String {
        return "XMake"
    }

    override fun getVersionString(sdk: Sdk): String {
        return SystemUtils.xmakeVersion
    }

    override fun createAdditionalDataConfigurable(sdkModel: SdkModel, sdkModificator: SdkModificator): AdditionalDataConfigurable? {
        return null
    }

    override fun getPresentableName(): String {
        return "XMake SDK"
    }

    override fun saveAdditionalData(sdkAdditionalData: SdkAdditionalData, element: Element) {
    }

    override fun getIcon(): Icon {
        return XMakeIcons.FILE
    }

    override fun getIconForAddAction(): Icon {
        return XMakeIcons.FILE
    }

    companion object {

        // the log
        private val Log = Logger.getInstance(XMakeSdkType::class.java.getName())

        // the instance
        val instance: XMakeSdkType
            get() = findInstance(XMakeSdkType::class.java)
    }
}
