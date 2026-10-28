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
 * @file        Loader.kt
 *
 */
package io.xmake.utils.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** The EDT dispatcher that also runs under an open modal dialog, for applying loaded
 *  results to the UI. */
internal val uiEdt: CoroutineContext
    get() = Dispatchers.EDT + ModalityState.any().asContextElement()

/** Decorates one suspend block into a UI-triggerable load: each call runs the block for the
 *  newest key only, cancelling the run still in flight. */
internal fun <K : Any> loader(
    scope: CoroutineScope,
    block: suspend (K) -> Unit,
): (K) -> Unit {
    val questions = Channel<K>(Channel.CONFLATED)
    scope.launch {
        questions.consumeAsFlow().collectLatest { key -> block(key) }
    }
    return { key -> questions.trySend(key) }
}
