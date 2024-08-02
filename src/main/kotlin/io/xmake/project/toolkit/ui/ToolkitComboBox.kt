package io.xmake.project.toolkit.ui

import ai.grazie.utils.tryRunWithException

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SortedComboBoxModel
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitManager
import javax.swing.event.PopupMenuEvent
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
        return model.selectedItem?:null
    }

    override fun setItem(anObject: ToolkitListItem?) {
        model.selectedItem = anObject
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
        this.putClientProperty("ComboBox.jbPopup.supportUpdateModel", true)
        Log.debug("ComboBox Client Property: " +
                this.getClientProperty("ComboBox.jbPopup.supportUpdateModel"))

    }

    init {
        val popupMenuListener = object : PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                super.popupMenuWillBecomeVisible(e)

                // todo: check whether safe or not
                val itemToolkit = (item as? ToolkitListItem.ToolkitItem)?.toolkit

                with(model){
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
                        if (it == null){
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
        }

        val detectListener = object : ToolkitManager.ToolkitDetectedListener {
            override fun onToolkitDetected(e: ToolkitManager.ToolkitDetectEvent) {
                val toolkit = e.source as Toolkit
                model.add(ToolkitListItem.ToolkitItem(toolkit))
                tryRunWithException<ClassCastException, Unit> {
                    firePropertyChange("model", false, true)
                }
            }

            override fun onAllToolkitsDetected() {}
        }

        this.addPopupMenuListener(popupMenuListener)
        service.addToolkitDetectedListener(detectListener)

        // Todo: refactor logic with listener
        whenItemSelected<ToolkitListItem> { toolkitListItem ->
            if (toolkitListItem is ToolkitListItem.ToolkitItem) {
                with(service){
                    val fetchedToolkit = toolkitSet.find { it.id == toolkitListItem.id }

                    if (fetchedToolkit != null) {
                        // check whether registered or not
                        state.registeredToolkits.run {
                            if (findRegisteredToolkitById(fetchedToolkit.id) == null){
                                add(fetchedToolkit)
                            }
                        }
                    } else {
                        // selectedItem toolkit is not in toolkitList
                    }

                    activatedToolkit = fetchedToolkit
                }
            }

            Log.info("selected Item: " + (item?.text ?: ""))

        }
    }

    companion object {
        private val Log = logger<ToolkitComboBox>()
    }

}


