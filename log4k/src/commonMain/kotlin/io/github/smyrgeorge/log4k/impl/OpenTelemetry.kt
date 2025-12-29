@file:Suppress("unused")

package io.github.smyrgeorge.log4k.impl

/**
 * The `OpenTelemetry` object provides a set of constants used for tracing and monitoring
 * various parts of an application or system. These constants represent standard attributes
 * that can be used to describe exceptions, HTTP requests and responses, database operations,
 * and thread states.
 */
object OpenTelemetry {
    //@formatter:off
    // Exception Attributes
    const val EXCEPTION = "exception"
    const val EXCEPTION_MESSAGE = "exception.message"                  // The exception message or error message
    const val EXCEPTION_STACKTRACE = "exception.stacktrace"            // Full stack trace of the exception
    const val EXCEPTION_TYPE = "exception.type"                        // Type or class of the exception (e.g., IOException, NullPointerException)
    const val EXCEPTION_ESCAPED = "exception.escaped"                  // Indicates if the exception was caught (false) or escaped the span (true)
    const val EXCEPTION_CAUGHT = "exception.caught"                    // Boolean indicating whether the exception was caught in the current span
    const val EXCEPTION_CAUSE_TYPE = "exception.cause.type"             // Type or class of the root cause exception (if there is a cause chain)
    const val EXCEPTION_CAUSE_MESSAGE = "exception.cause.message"       // The message from the root cause exception
    const val EXCEPTION_CAUSE_STACKTRACE = "exception.cause.stacktrace" // Stack trace of the root cause exception
    const val EXCEPTION_HANDLED_AT = "exception.handled_at"             // The location (file, method, or class) where the exception was handled
    const val EXCEPTION_TIMESTAMP = "exception.timestamp"               // The time when the exception occurred

    // HTTP Attributes (following OpenTelemetry semantic conventions)
    const val HTTP_REQUEST_METHOD = "http.request.method"              // HTTP method (e.g., GET, POST, PUT)
    const val HTTP_RESPONSE_STATUS_CODE = "http.response.status_code"  // HTTP response status code (e.g., 200, 404)
    const val HTTP_ROUTE = "http.route"                                // Matched route (for server spans)
    const val URL_FULL = "url.full"                                    // Full URL of the HTTP request
    const val URL_PATH = "url.path"                                    // The path of the HTTP request
    const val URL_SCHEME = "url.scheme"                                // URL scheme (http or https)
    const val URL_QUERY = "url.query"                                  // Query string of the URL
    const val SERVER_ADDRESS = "server.address"                        // Server domain name or IP address
    const val SERVER_PORT = "server.port"                              // Server port number
    const val CLIENT_ADDRESS = "client.address"                        // Client's IP address
    const val CLIENT_PORT = "client.port"                              // Client's port number
    const val USER_AGENT_ORIGINAL = "user_agent.original"              // Value of the User-Agent header in the request
    const val NETWORK_PROTOCOL_VERSION = "network.protocol.version"    // HTTP protocol version (e.g., 1.1, 2.0)
    const val HTTP_REQUEST_BODY_SIZE = "http.request.body.size"        // Size of the request body in bytes
    const val HTTP_RESPONSE_BODY_SIZE = "http.response.body.size"      // Size of the response body in bytes

    // SQL Attributes
    const val DB_SYSTEM = "db.system"                                  // The type of the database (e.g., mysql, postgresql, sqlite)
    const val DB_CONNECTION_STRING = "db.connection_string"            // Connection string used to connect to the database
    const val DB_USER = "db.user"                                      // The database username
    const val DB_NAME = "db.name"                                      // The name of the database being accessed
    const val DB_STATEMENT = "db.statement"                            // The actual SQL query or command executed
    const val DB_OPERATION = "db.operation"                            // Type of operation (e.g., SELECT, INSERT, UPDATE)
    const val DB_SQL_TABLE = "db.sql.table"                            // The table involved in the query (if applicable)
    const val DB_SQL_ROW_COUNT = "db.sql.row_count"                    // Number of rows returned or affected by the query
    const val DB_STATEMENT_ERROR = "db.statement.error"                // Error message if the query fails
    const val DB_DRIVER_NAME = "db.driver.name"                        // Name of the database driver (e.g., org.postgresql.Driver)
    const val DB_DRIVER_VERSION = "db.driver.version"                  // Version of the database driver
    const val DB_CONNECTION_ID = "db.connection.id"                    // Identifier for the database connection

    // Thread Attributes
    const val THREAD_ID = "thread.id"                                  // The system ID of the thread (native thread ID)
    const val THREAD_NAME = "thread.name"                              // The name of the thread (as set by the application or JVM)
    const val THREAD_STATE = "thread.state"                            // The current state of the thread (e.g., RUNNABLE, BLOCKED, WAITING)
    const val THREAD_PRIORITY = "thread.priority"                      // The priority of the thread (if applicable)
    const val THREAD_CPU_TIME = "thread.cpu_time"                      // The CPU time consumed by the thread (in nanoseconds, if available)
    const val THREAD_BLOCKED_TIME = "thread.blocked_time"              // Time spent blocked (waiting for a monitor or lock)
    const val THREAD_WAITING_TIME = "thread.waiting_time"              // Time spent waiting (in WAITING or TIMED_WAITING state)

    // User & Identity Attributes
    const val USER_ID = "user.id"                                      // User identifier
    const val USER_NAME = "user.name"                                  // User's name or username
    const val USER_FULL_NAME = "user.full_name"                        // Full name of the user
    const val USER_EMAIL = "user.email"                                // User email address
    const val USER_HASH = "user.hash"                                  // Hashed user identifier for privacy
    const val USER_ROLES = "user.roles"                                // User roles

    // Session & Transaction Attributes
    const val SESSION_ID = "session.id"                                // Session identifier
    const val TRANSACTION_ID = "transaction.id"                        // Transaction or correlation identifier
    const val REQUEST_ID = "request.id"                                // Unique request identifier
    const val TRACE_ID = "trace.id"                                    // Distributed trace identifier
    const val SPAN_ID = "span.id"                                      // Span identifier within a trace

    // Service & Deployment Attributes
    const val SERVICE_NAME = "service.name"                            // Name of the service
    const val SERVICE_VERSION = "service.version"                      // Version of the service
    const val SERVICE_NAMESPACE = "service.namespace"                  // Namespace of the service (e.g., production, staging)
    const val SERVICE_INSTANCE_ID = "service.instance.id"              // Unique instance identifier
    const val DEPLOYMENT_ENVIRONMENT = "deployment.environment"        // Deployment environment (e.g., production, staging, dev)

    // Code Location Attributes
    const val CODE_FUNCTION = "code.function"                          // Function or method name
    const val CODE_NAMESPACE = "code.namespace"                        // Namespace or package name
    const val CODE_FILEPATH = "code.filepath"                          // Source file path
    const val CODE_LINENO = "code.lineno"                              // Line number in the source file

    // Event & Message Attributes
    const val EVENT_NAME = "event.name"                                // Name of the event
    const val EVENT_DOMAIN = "event.domain"                            // Domain of the event (e.g., user, system, payment)
    const val MESSAGE_TYPE = "message.type"                            // Type of message (e.g., SENT, RECEIVED)
    const val MESSAGE_ID = "message.id"                                // Message identifier
    const val MESSAGE_COMPRESSED_SIZE = "message.compressed_size"      // Size of the compressed message in bytes
    const val MESSAGE_UNCOMPRESSED_SIZE = "message.uncompressed_size"  // Size of the uncompressed message in bytes
    //@formatter:on
}
