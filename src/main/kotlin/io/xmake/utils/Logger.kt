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
package io.xmake.utils

import com.intellij.openapi.diagnostic.logger

/**
 * XMake Logger utility with different log levels and tag support
 * Provides a unified logging interface for the XMake plugin
 * Uses println in debug mode and IntelliJ logger in production
 */
object Logger {
    
    // Log levels
    enum class LogLevel {
        DEBUG,
        VERBOSE, 
        INFO,
        WARN,
        ERROR
    }
    
    // Default tag
    private const val DEFAULT_TAG = "XMake"
    
    // Use IntelliJ logger in production, println in debug
    private val useIntelliJLogger = java.lang.Boolean.getBoolean("xmake.production") && !java.lang.Boolean.getBoolean("xmake.debug")
    
    // Current log level (can be configured)
    private var currentLogLevel = if (useIntelliJLogger) LogLevel.INFO else LogLevel.DEBUG
    
    // IntelliJ logger instances
    private val loggers = mutableMapOf<String, com.intellij.openapi.diagnostic.Logger>()
    
    /**
     * Set the current log level
     */
    fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
    }
    
    /**
     * Get current logging mode (for debugging)
     */
    fun getLoggingMode(): String {
        return if (useIntelliJLogger) "IntelliJ Logger" else "Console (println)"
    }
    
    /**
     * Debug level log
     */
    fun d(tag: String = DEFAULT_TAG, message: String) {
        log(LogLevel.DEBUG, tag, message)
    }
    
    /**
     * Debug level log with default tag
     */
    fun d(message: String) {
        d(DEFAULT_TAG, message)
    }
    
    /**
     * Verbose level log
     */
    fun v(tag: String = DEFAULT_TAG, message: String) {
        log(LogLevel.VERBOSE, tag, message)
    }
    
    /**
     * Verbose level log with default tag
     */
    fun v(message: String) {
        v(DEFAULT_TAG, message)
    }
    
    /**
     * Info level log (default)
     */
    fun i(tag: String = DEFAULT_TAG, message: String) {
        log(LogLevel.INFO, tag, message)
    }
    
    /**
     * Info level log with default tag
     */
    fun i(message: String) {
        i(DEFAULT_TAG, message)
    }
    
    /**
     * Warning level log
     */
    fun w(tag: String = DEFAULT_TAG, message: String) {
        log(LogLevel.WARN, tag, message)
    }
    
    /**
     * Warning level log with default tag
     */
    fun w(message: String) {
        w(DEFAULT_TAG, message)
    }
    
    /**
     * Error level log
     */
    fun e(tag: String = DEFAULT_TAG, message: String) {
        log(LogLevel.ERROR, tag, message)
    }
    
    /**
     * Error level log with default tag
     */
    fun e(message: String) {
        e(DEFAULT_TAG, message)
    }
    
    /**
     * Error level log with exception
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable) {
        log(LogLevel.ERROR, tag, "$message: ${throwable.message}")
        if (useIntelliJLogger) {
            getLogger(tag).error(throwable)
        } else {
            throwable.printStackTrace()
        }
    }
    
    /**
     * Error level log with exception and default tag
     */
    fun e(message: String, throwable: Throwable) {
        e(DEFAULT_TAG, message, throwable)
    }
    
    /**
     * Core logging method - chooses between println and IntelliJ logger
     */
    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < currentLogLevel.ordinal) {
            return
        }
        
        if (useIntelliJLogger) {
            // Use IntelliJ logger in production
            val logger = getLogger(tag)
            when (level) {
                LogLevel.DEBUG -> logger.debug(message)
                LogLevel.VERBOSE -> logger.debug(message) // IntelliJ doesn't have VERBOSE, use DEBUG
                LogLevel.INFO -> logger.info(message)
                LogLevel.WARN -> logger.warn(message)
                LogLevel.ERROR -> logger.error(message)
            }
        } else {
            // Use println in debug mode
            val timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
    
    /**
     * Get or create IntelliJ logger for the given tag
     */
    private fun getLogger(tag: String): com.intellij.openapi.diagnostic.Logger {
        return loggers.getOrPut(tag) {
            logger<Logger>()
        }
    }
}
