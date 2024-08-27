package io.xmake.project.wizard

import com.intellij.ide.wizard.NewProjectWizardBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.util.Key
import io.xmake.project.toolkit.Toolkit

interface XMakeNewProjectWizardData : NewProjectWizardBaseData {

    val remotePathProperty: GraphProperty<String>

    var remotePath: String

    val remoteContentEntryPath: String // canonical
        get() = "$remotePath/$name"

    val toolkitProperty: GraphProperty<Toolkit?>

    var toolkit: Toolkit?

    val languagesProperty: GraphProperty<String>

    var language: String

    val kindsProperty: GraphProperty<String>

    var kind: String

    override val contentEntryPath: String
        get() = "$path/$name"

    companion object {

        val KEY: Key<XMakeNewProjectWizardData> = Key.create(XMakeNewProjectWizardData::class.java.name)

        @JvmStatic
        val NewProjectWizardStep.xmakeData: XMakeNewProjectWizardData?
            get() = data.getUserData(KEY)
    }
}