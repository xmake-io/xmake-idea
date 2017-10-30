package org.tboox.xmake.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

class XMakeRunConfigurationProducer : RunConfigurationProducer<XMakeRunConfiguration>(XMakeRunConfigurationType()) {

    override fun isConfigurationFromContext(
            configuration: XMakeRunConfiguration,
            context: ConfigurationContext
    ): Boolean {

        Log.info("isConfigurationFromContext")
        return false
    }

    override fun setupConfigurationFromContext(
            configuration: XMakeRunConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>
    ): Boolean {

        Log.info("setupConfigurationFromContext")
        return true
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationProducer::class.java.getName())
    }
}
