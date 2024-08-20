package io.xmake.project.toolkit.ui

import ai.grazie.utils.tryRunWithException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.transformParameter
import com.intellij.openapi.ui.validation.validationErrorIf
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SortedComboBoxModel
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitManager
import java.awt.event.ItemEvent
import java.util.*
import javax.swing.event.PopupMenuEvent
import kotlin.concurrent.timerTask
import kotlin.reflect.KMutableProperty0

class ToolkitComboBox(toolkitProperty: KMutableProperty0<Toolkit?>) : ComboBox<ToolkitListItem>(
    SortedComboBoxModel { o1, o2 -> o1 compareTo o2 }
) {

    private val service = ToolkitManager.getInstance()

    private var model: SortedComboBoxModel<ToolkitListItem>
        get() = super.getModel() as SortedComboBoxModel<ToolkitListItem>
        set(value) { super.setModel(value) }

    var activatedToolkit: Toolkit? by toolkitProperty
        private set

    override fun getItem(): ToolkitListItem? {
        return model.selectedItem ?: null
    }

    override fun setItem(anObject: ToolkitListItem?) {
        model.selectedItem = anObject
    }

    private val timer = Timer()
    private var timerTask: TimerTask? = null

    private fun debounce(delayMillis: Long = 50L, action: TimerTask.() -> Unit) {
        timerTask?.cancel()
        timerTask = timerTask(action)
        timer.schedule(timerTask, delayMillis)
    }

    init {
        model.apply {
            val initialToolkit = activatedToolkit
            Log.debug("ComboBox initial activated Toolkit: $initialToolkit")

            val initialListItem = if (initialToolkit != null) {
                if (initialToolkit.isValid)
                    ToolkitListItem.ToolkitItem(initialToolkit)
                else
                    ToolkitListItem.ToolkitItem(initialToolkit).asInvalid()
            } else {
                ToolkitListItem.NoneItem()
            }

            add(initialListItem)
            item = initialListItem
        }

        isSwingPopup = false
        maximumRowCount = 30
        renderer = ToolkitComboBoxRenderer(this)
        putClientProperty("ComboBox.jbPopup.supportUpdateModel", true)
        Log.debug("ComboBox Client Property: " + getClientProperty("ComboBox.jbPopup.supportUpdateModel"))

    }

    init {
        addPopupMenuListener(object : PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                super.popupMenuWillBecomeVisible(e)

                // todo: check whether safe or not
                val itemToolkit = (item as? ToolkitListItem.ToolkitItem)?.toolkit

                with(model) {
                    clear()
                    add(ToolkitListItem.NoneItem())
                    service.state.registeredToolkits.forEach {
                        add(ToolkitListItem.ToolkitItem(it).asRegistered())
                    }
                }

                tryRunWithException<ClassCastException, Unit> {
                    firePropertyChange("model", false, true)
                }

                // to select configuration-level activated toolkit
                item = if (service.state.registeredToolkits.isEmpty() || itemToolkit == null) {
                    model.items.first()
                } else {
                    model.items.find { it.id == itemToolkit.id }.let {
                        if (it == null) {
                            val invalidToolkitItem = ToolkitListItem.ToolkitItem(itemToolkit).asInvalid()
                            model.items.add(0, invalidToolkitItem)
                            invalidToolkitItem
                        } else it
                    }
                }

                val project = ProjectManager.getInstanceIfCreated()?.defaultProject
                service.detectXmakeToolkits(project)
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                super.popupMenuWillBecomeInvisible(e)
                service.cancelDetection()
            }
        })

        service.addToolkitDetectedListener(object : ToolkitManager.ToolkitDetectedListener {
            override fun onToolkitDetected(e: ToolkitManager.ToolkitDetectEvent) {
                val toolkit = e.source as Toolkit
                model.add(ToolkitListItem.ToolkitItem(toolkit))
                debounce {
                    tryRunWithException<ClassCastException, Unit> {
                        firePropertyChange("model", false, true)
                    }
                }
            }

            override fun onAllToolkitsDetected() {}
        })

        addItemListener { it ->
            if (it.stateChange == ItemEvent.SELECTED) {
                val toolkitListItem = it.item as ToolkitListItem
                if (toolkitListItem is ToolkitListItem.ToolkitItem) {
                    with(service) {
                        val fetchedToolkit = toolkitSet.find { it.id == toolkitListItem.id }

                        if (fetchedToolkit != null) {
                            // check whether registered or not
                            state.registeredToolkits.run {
                                if (findRegisteredToolkitById(fetchedToolkit.id) == null) {
                                    add(fetchedToolkit)
                                }
                            }
                        } else {
                            // selectedItem toolkit is not in toolkitSet
                        }
                        if (fetchedToolkit != null) {
                            // check whether registered or not
                            state.registeredToolkits.run {
                                if (findRegisteredToolkitById(fetchedToolkit.id) == null) {
                                    add(fetchedToolkit)
                                }
                            }
                        } else {
                            // selectedItem toolkit is not in toolkitSet
                        }

                        activatedToolkit = fetchedToolkit
                    }
                } else {
                    activatedToolkit = null
                }

                toolkitChangedListeners.forEach { listener ->
                    listener.onToolkitChanged(activatedToolkit)
                }

                Log.info("activeToolkit: $activatedToolkit")
                Log.info("selected Item: " + (item?.text ?: ""))
            }
        }
    }

    private val toolkitChangedListeners = mutableListOf<ToolkitChangedListener>()

    interface ToolkitChangedListener : EventListener {
        fun onToolkitChanged(toolkit: Toolkit?)
    }

    fun addToolkitChangedListener(listener: ToolkitChangedListener) {
        toolkitChangedListeners.add(listener)
    }

    fun addToolkitChangedListener(action: (Toolkit?) -> Unit) {
        toolkitChangedListeners.add(object : ToolkitChangedListener {
            override fun onToolkitChanged(toolkit: Toolkit?) {
                action(toolkit)
            }
        })
    }

    fun removeToolkitChangedListener(listener: ToolkitChangedListener) {
        toolkitChangedListeners.remove(listener)
    }

    fun getToolkitChangedListeners(): List<ToolkitChangedListener> = toolkitChangedListeners

    companion object {
        private val Log = logger<ToolkitComboBox>()

        fun DialogValidation.WithParameter<() -> Toolkit?>.forToolkitComboBox(): DialogValidation.WithParameter<ToolkitComboBox> =
            transformParameter { ::activatedToolkit }

        val CHECK_NON_EMPTY_TOOLKIT: DialogValidation.WithParameter<() -> Toolkit?> =
            validationErrorIf<Toolkit?>("XMake toolkit is not set!") { it == null }
    }

}


