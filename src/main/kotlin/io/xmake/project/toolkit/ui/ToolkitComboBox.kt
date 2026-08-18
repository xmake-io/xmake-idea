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
 * @file        ToolkitComboBox.kt
 *
 */
package io.xmake.project.toolkit.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.transformParameter
import com.intellij.openapi.ui.validation.validationErrorIf
import com.intellij.ui.PopupMenuListenerAdapter
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.utils.ui.LiveModelComboBox
import java.awt.event.ItemEvent
import javax.swing.event.PopupMenuEvent
import kotlin.reflect.KMutableProperty0

class ToolkitComboBox(
    private val project: Project?,
    selectedToolkitProperty: KMutableProperty0<Toolkit?>,
) : LiveModelComboBox<ToolkitListItem>(ToolkitComboBoxModel()) {

    private val toolkitManager = ToolkitManager.getInstance()
    private val selectionListeners = mutableListOf<(Toolkit?) -> Unit>()
    private var isDisposed = false

    var selectedToolkit: Toolkit? by selectedToolkitProperty
        private set

    private val toolkitModel: ToolkitComboBoxModel
        get() = super.getModel() as ToolkitComboBoxModel

    init {
        maximumRowCount = 30
        renderer = ToolkitComboBoxRenderer(this)
        synchronizeWithToolkits()

        (project?.messageBus ?: ApplicationManager.getApplication().messageBus).connect(this).subscribe(
            ToolkitListener.TOPIC,
            object : ToolkitListener {
                override fun toolkitsChanged() {
                    ApplicationManager.getApplication().invokeLater(
                        { if (!isDisposed) synchronizeAndNotify() },
                        ModalityState.any(),
                    )
                }
            },
        )

        addPopupMenuListener(object : PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent?) {
                synchronizeAndNotify()
                refreshPopupFromModel()
                toolkitManager.requestScan(project)
            }
        })

        addItemListener { event ->
            if (event.stateChange != ItemEvent.SELECTED || isSelectionHandlingSuppressed) return@addItemListener

            val toolkitId = (event.item as? ToolkitListItem.Entry)
                ?.id
            val registeredToolkit = toolkitId?.let { id -> toolkitManager.register(id, project) }
            if (synchronizeWithToolkits(registeredToolkit)) fireSelectionChanged()
        }
    }

    override fun addNotify() {
        super.addNotify()
        synchronizeAndNotify()
        toolkitManager.requestScan(project)
    }

    fun addSelectionListener(listener: (Toolkit?) -> Unit) {
        selectionListeners.add(listener)
    }

    fun selectToolkit(toolkit: Toolkit?) {
        if (synchronizeWithToolkits(toolkit)) fireSelectionChanged()
    }

    private fun synchronizeWithToolkits(requestedToolkit: Toolkit? = selectedToolkit): Boolean {
        val visibleToolkits = toolkitManager.visibleToolkits(project)
        val previousToolkit = selectedToolkit
        val resolvedToolkit = requestedToolkit?.id?.let { id ->
            visibleToolkits.firstOrNull { toolkit -> toolkit.id == id }?.takeIf { it.isRegistered }
        }
        selectedToolkit = resolvedToolkit
        val selectionChanged = when {
            previousToolkit === resolvedToolkit -> false
            previousToolkit == null || resolvedToolkit == null -> true
            else -> !previousToolkit.isSameSnapshotAs(resolvedToolkit)
        }
        val itemsChanged = withSelectionHandlingSuppressed {
            toolkitModel.synchronizeWith(visibleToolkits, selectedToolkit?.id)
        }
        if (itemsChanged) schedulePopupRefresh()
        return selectionChanged
    }

    private fun synchronizeAndNotify() {
        if (synchronizeWithToolkits()) fireSelectionChanged()
    }

    private fun fireSelectionChanged() {
        selectionListeners.toList().forEach { listener -> listener(selectedToolkit) }
    }

    override fun dispose() {
        isDisposed = true
        super.dispose()
        selectionListeners.clear()
    }

    companion object {
        fun DialogValidation.WithParameter<() -> Toolkit?>.forToolkitComboBox(): DialogValidation.WithParameter<ToolkitComboBox> =
            transformParameter { ::selectedToolkit }

        val REQUIRE_TOOLKIT_SELECTION: DialogValidation.WithParameter<() -> Toolkit?> =
            validationErrorIf("XMake toolkit is not set!") { toolkit: Toolkit? -> toolkit == null }
    }
}
