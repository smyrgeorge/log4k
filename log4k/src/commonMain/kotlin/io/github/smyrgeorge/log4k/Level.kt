package io.github.smyrgeorge.log4k

/**
 * Represents the logging levels used for configuring and filtering log messages.
 *
 * Each level defines specific severity or importance for log entries,
 * allowing developers to control which messages are recorded or displayed.
 *
 * Levels:
 * - TRACE: Fine-grained information, typically used for debugging.
 * - DEBUG: Provides debugging information useful for development.
 * - INFO: General information about the application's progress or state.
 * - WARN: Indicates potentially harmful situations or unexpected behavior.
 * - ERROR: Captures error events that might disrupt the program's execution.
 * - OFF: Disables logging entirely.
 */
enum class Level {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF
}
