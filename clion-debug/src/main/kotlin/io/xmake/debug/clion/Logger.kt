package io.xmake.debug.clion

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple logger implementation for CLion debug module
 * This is a copy of the main Logger utility but simplified for the debug module
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
