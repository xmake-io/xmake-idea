package org.tboox.xmake.project

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.Sdk
import org.tboox.xmake.icons.XMakeIcons
import org.jdom.Element
import org.tboox.xmake.utils.SystemUtils
import java.io.File
import javax.swing.Icon

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

    override fun getVersionString(sdk: Sdk): String? {
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
            get() = SdkType.findInstance(XMakeSdkType::class.java)
    }
}
