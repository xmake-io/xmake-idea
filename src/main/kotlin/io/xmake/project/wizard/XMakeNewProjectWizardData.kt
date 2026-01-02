/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        XMakeNewProjectWizardData.kt
 *
 */
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