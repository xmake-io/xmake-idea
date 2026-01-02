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
 * @file        SftpChannelEx.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.openapi.diagnostic.logger
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.channels.isDir
import com.intellij.util.io.systemIndependentPath
import kotlin.io.path.Path

val SftpChannel.Log by lazy { logger<SftpChannel>() }

fun SftpChannel.rmRecur(path: String){
    ls(path).forEach {
        if (it.attrs.isDir) {
            Log.info("recur: ${Path(path, it.name)}")
            rmRecur(Path(path, it.name).systemIndependentPath)
        } else {
            Log.info("rm: ${Path(path, it.name).systemIndependentPath}")
            rm(Path(path, it.name).systemIndependentPath)
        }
    }
    Log.info("rmdir: $path")
    rmdir(path)

}