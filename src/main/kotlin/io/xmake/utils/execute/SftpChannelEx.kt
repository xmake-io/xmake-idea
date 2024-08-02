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