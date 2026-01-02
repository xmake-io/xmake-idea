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
 * @file        Logger.kt
 *
 */
package io.xmake.debug.clion.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple logger implementation for CLion debug module
 */
object Logger {
    
    private const val DEFAULT_TAG = "ClionDebugModule"
    
    // Log levels
    enum class LogLevel {
        DEBUG,
        VERBOSE, 
        INFO,
        WARN,
        ERROR
    }
    
    // Current log level (can be configured)
    private var currentLogLevel = LogLevel.DEBUG
    
    /**
     * Set the current log level
     */
    fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
    }
    
    /**
     * Debug level log with default tag
     */
    fun d(message: String) {
        d(DEFAULT_TAG, message)
    }
    
    /**
     * Debug level log with tag
     */
    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }
    
    /**
     * Verbose level log with default tag
     */
    fun v(message: String) {
        v(DEFAULT_TAG, message)
    }
    
    /**
     * Verbose level log with tag
     */
    fun v(tag: String, message: String) {
        log(LogLevel.VERBOSE, tag, message)
    }
    
    /**
     * Info level log with default tag
     */
    fun i(message: String) {
        i(DEFAULT_TAG, message)
    }
    
    /**
     * Info level log with tag
     */
    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
    }
    
    /**
     * Warning level log with default tag
     */
    fun w(message: String) {
        w(DEFAULT_TAG, message)
    }
    
    /**
     * Warning level log with tag
     */
    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
    }
    
    /**
     * Warning level log with exception and default tag
     */
    fun w(message: String, throwable: Throwable) {
        w(DEFAULT_TAG, message, throwable)
    }
    
    /**
     * Warning level log with exception and tag
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.WARN, tag, message)
        throwable.printStackTrace()
    }
    
    /**
     * Error level log with default tag
     */
    fun e(message: String) {
        e(DEFAULT_TAG, message)
    }
    
    /**
     * Error level log with tag
     */
    fun e(tag: String, message: String) {
        log(LogLevel.ERROR, tag, message)
    }
    
    /**
     * Error level log with exception and default tag
     */
    fun e(message: String, throwable: Throwable) {
        e(DEFAULT_TAG, message, throwable)
    }
    
    /**
     * Error level log with exception and tag
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        log(LogLevel.ERROR, tag, message)
        throwable.printStackTrace()
    }
    
    /**
     * Core logging method
     */
    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < currentLogLevel.ordinal) {
            return
        }
        
        // Use println for all logging in the debug module
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        
        val levelChar = when (level) {
            LogLevel.DEBUG -> "D"
            LogLevel.VERBOSE -> "V"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        
        println("[$timestamp] $levelChar/$tag: $message")
    }
}
