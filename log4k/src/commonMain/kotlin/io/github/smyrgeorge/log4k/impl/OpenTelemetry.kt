package io.github.smyrgeorge.log4k.impl

@Suppress("unused")
object OpenTelemetry {
    // Exception Attributes
    const val EXCEPTION = "exception"
    const val EXCEPTION_MESSAGE = "exception.message"           // The exception message or error message
    const val EXCEPTION_STACKTRACE = "exception.stacktrace"     // Full stack trace of the exception
    const val EXCEPTION_TYPE = "exception.type"                 // Type or class of the exception (e.g., IOException, NullPointerException)
    const val EXCEPTION_ESCAPED = "exception.escaped"           // Indicates if the exception was caught (false) or escaped the span (true)
    const val EXCEPTION_CAUGHT = "exception.caught"             // Boolean indicating whether the exception was caught in the current span
    const val EXCEPTION_CAUSE_TYPE = "exception.cause.type"     // Type or class of the root cause exception (if there is a cause chain)
    const val EXCEPTION_CAUSE_MESSAGE = "exception.cause.message" // The message from the root cause exception
    const val EXCEPTION_CAUSE_STACKTRACE = "exception.cause.stacktrace" // Stack trace of the root cause exception
    const val EXCEPTION_HANDLED_AT = "exception.handled_at"     // The location (file, method, or class) where the exception was handled
    const val EXCEPTION_TIMESTAMP = "exception.timestamp"       // The time when the exception occurred

    // HTTP Attributes
    const val HTTP_METHOD = "http.method"                 // HTTP method (e.g., GET, POST, PUT)
    const val HTTP_URL = "http.url"                       // Full URL of the HTTP request
    const val HTTP_TARGET = "http.target"                 // The target of the HTTP request (e.g., path in the URL)
    const val HTTP_HOST = "http.host"                     // Host header value (e.g., www.example.com)
    const val HTTP_SCHEME = "http.scheme"                 // URL scheme (http or https)
    const val HTTP_STATUS_CODE = "http.status_code"       // HTTP response status code (e.g., 200, 404)
    const val HTTP_FLAVOR = "http.flavor"                 // HTTP protocol version (e.g., 1.1, 2.0)
    const val HTTP_USER_AGENT = "http.user_agent"         // Value of the User-Agent header in the request
    const val HTTP_SERVER_NAME = "http.server_name"       // The server name as defined by the host or service
    const val HTTP_ROUTE = "http.route"                   // Matched route (for server spans)
    const val HTTP_CLIENT_IP = "http.client_ip"           // Client's IP address
    const val HTTP_REQUEST_CONTENT_LENGTH = "http.request_content_length"   // Size of the request body in bytes
    const val HTTP_RESPONSE_CONTENT_LENGTH = "http.response_content_length" // Size of the response body in bytes
    const val HTTP_ERROR_MESSAGE = "http.error_message"   // Error message when an HTTP error occurs

    // SQL Attributes
    const val DB_SYSTEM = "db.system"                    // The type of the database (e.g., mysql, postgresql, sqlite)
    const val DB_CONNECTION_STRING = "db.connection_string"  // Connection string used to connect to the database
    const val DB_USER = "db.user"                        // The database username
    const val DB_NAME = "db.name"                        // The name of the database being accessed
    const val DB_STATEMENT = "db.statement"              // The actual SQL query or command executed
    const val DB_OPERATION = "db.operation"              // Type of operation (e.g., SELECT, INSERT, UPDATE)
    const val DB_SQL_TABLE = "db.sql.table"              // The table involved in the query (if applicable)
    const val DB_SQL_ROW_COUNT = "db.sql.row_count"      // Number of rows returned or affected by the query
    const val DB_STATEMENT_ERROR = "db.statement.error"  // Error message if the query fails
    const val DB_DRIVER_NAME = "db.driver.name"          // Name of the database driver (e.g., org.postgresql.Driver)
    const val DB_DRIVER_VERSION = "db.driver.version"    // Version of the database driver
    const val DB_CONNECTION_ID = "db.connection.id"      // Identifier for the database connection

    // Thread Attributes
    const val THREAD_ID = "thread.id"                     // The system ID of the thread (native thread ID)
    const val THREAD_NAME = "thread.name"                 // The name of the thread (as set by the application or JVM)
    const val THREAD_STATE = "thread.state"               // The current state of the thread (e.g., RUNNABLE, BLOCKED, WAITING)
    const val THREAD_PRIORITY = "thread.priority"         // The priority of the thread (if applicable)
    const val THREAD_CPU_TIME = "thread.cpu_time"         // The CPU time consumed by the thread (in nanoseconds, if available)
    const val THREAD_BLOCKED_TIME = "thread.blocked_time" // Time spent blocked (waiting for a monitor or lock)
    const val THREAD_WAITING_TIME = "thread.waiting_time" // Time spent waiting (in WAITING or TIMED_WAITING state)
}