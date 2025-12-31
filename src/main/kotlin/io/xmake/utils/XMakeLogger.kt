package io.xmake.utils

/**
 * XMake Logger utility with different log levels and tag support
 * Provides a unified logging interface for the XMake plugin
 */
object XMakeLogger {
    
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
    
    // Current log level (can be configured)
    private var currentLogLevel = LogLevel.INFO
    
    /**
     * Set the current log level
     */
    fun setLogLevel(level: LogLevel) {
        currentLogLevel = level
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
        throwable.printStackTrace()
    }
    
    /**
     * Error level log with exception and default tag
     */
    fun e(message: String, throwable: Throwable) {
        e(DEFAULT_TAG, message, throwable)
    }
    
    /**
     * Core logging method
     */
    private fun log(level: LogLevel, tag: String, message: String) {
        if (level.ordinal < currentLogLevel.ordinal) {
            return
        }
        
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
