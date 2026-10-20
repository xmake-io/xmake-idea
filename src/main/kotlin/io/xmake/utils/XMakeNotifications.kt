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
 * @file        XMakeNotifications.kt
 *
 */
package io.xmake.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project

private const val NOTIFICATION_GROUP = "XMake.NotificationGroup"

/** Shows a notification from any thread; delivery is posted to the EDT and skipped after disposal. */
fun Project.notify(
    message: String,
    type: NotificationType = NotificationType.ERROR,
    title: String? = null,
) {
    val application = ApplicationManager.getApplication()
    val show = {
        if (!isDisposed) {
            val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            val notification = if (title == null) {
                group.createNotification(message, type)
            } else {
                group.createNotification(title, message, type)
            }
            notification.notify(this)
        }
    }
    if (application.isDispatchThread) show() else application.invokeLater(show, ModalityState.any())
}
