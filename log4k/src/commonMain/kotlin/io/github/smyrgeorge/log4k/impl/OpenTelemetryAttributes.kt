@file:Suppress("unused")

package io.github.smyrgeorge.log4k.impl

/**
 * The `OpenTelemetry` object provides a set of constants used for tracing and monitoring
 * various parts of an application or system. These constants represent standard attributes
 * that can be used to describe exceptions, HTTP requests and responses, database operations,
 * and thread states.
 *
 * https://opentelemetry.io/docs/specs/semconv/registry/attributes/
 */
object OpenTelemetryAttributes {
    //@formatter:off
    // Exception Attributes
    const val EXCEPTION = "exception"
    const val EXCEPTION_MESSAGE = "exception.message"                   // The exception message or error message
    const val EXCEPTION_STACKTRACE = "exception.stacktrace"             // Full stack trace of the exception
    const val EXCEPTION_TYPE = "exception.type"                         // Type or class of the exception (e.g., IOException, NullPointerException)
    @Deprecated("Deprecated in OpenTelemetry specification.")
    const val EXCEPTION_ESCAPED = "exception.escaped"                   // Indicates if the exception was caught (false) or escaped the span (true)

    // HTTP Attributes – Stable (OpenTelemetry)
    const val HTTP_REQUEST_METHOD = "http.request.method"               // HTTP request method (GET, POST, etc.) :contentReference[oaicite:0]{index=0}
    const val HTTP_REQUEST_METHOD_ORIGINAL = "http.request.method_original" // Original raw method string: contentReference[oaicite:1]{index=1}
    const val HTTP_REQUEST_RESEND_COUNT = "http.request.resend_count"   // Count of resend attempts: contentReference[oaicite:2]{index=2}
    const val HTTP_REQUEST_BODY_SIZE = "http.request.body.size"         // Size of the HTTP request body in bytes: contentReference[oaicite:3]{index=3}
    const val HTTP_RESPONSE_STATUS_CODE = "http.response.status_code"   // HTTP response status code :contentReference[oaicite:4]{index=4}
    const val HTTP_RESPONSE_BODY_SIZE = "http.response.body.size"       // Size of the HTTP response body in bytes: contentReference[oaicite:5]{index=5}
    const val HTTP_ROUTE = "http.route"                                 // Matched route template :contentReference[oaicite:6]{index=6}

    // URL Attributes – Stable / Development
    const val URL_FULL = "url.full"                                    // The full absolute URL (RFC3986) :contentReference[oaicite:11]{index=11}
    const val URL_PATH = "url.path"                                    // The URL path component :contentReference[oaicite:12]{index=12}
    const val URL_QUERY = "url.query"                                  // The URL query component :contentReference[oaicite:13]{index=13}
    const val URL_SCHEME = "url.scheme"                                // The URL scheme (http, https, etc.) :contentReference[oaicite:14]{index=14}

    // These attributes are also available (Development stability):
    const val URL_DOMAIN = "url.domain"                                // The registered domain/IP :contentReference[oaicite:15]{index=15}
    const val URL_REGISTERED_DOMAIN = "url.registered_domain"          // Registered top-level domain e.g., example.com :contentReference[oaicite:16]{index=16}
    const val URL_SUBDOMAIN = "url.subdomain"                          // Subdomain portion :contentReference[oaicite:17]{index=17}
    const val URL_TOP_LEVEL_DOMAIN = "url.top_level_domain"            // eTLD (like com, net) :contentReference[oaicite:18]{index=18}
    const val URL_PORT = "url.port"                                    // Port extracted from URL :contentReference[oaicite:19]{index=19}
    const val URL_EXTENSION = "url.extension"                          // File extension from path :contentReference[oaicite:20]{index=20}
    const val URL_FRAGMENT = "url.fragment"                            // URI fragment (#stuff) :contentReference[oaicite:21]{index=21}
    const val URL_ORIGINAL = "url.original"                            // The raw unmodified URL :contentReference[oaicite:22]{index=22}
    const val URL_TEMPLATE = "url.template"                            // Low-cardinality path template :contentReference[oaicite:23]{index=23}

    // Host Resource Attributes
    const val HOST_ID = "host.id"                                      // Unique host identifier (machine-id, cloud id) :contentReference[oaicite:26]{index=26}
    const val HOST_NAME = "host.name"                                  // Hostname :contentReference[oaicite:27]{index=27}
    const val HOST_IP = "host.ip"                                      // IP addresses (excluding loopback) :contentReference[oaicite:28]{index=28}
    const val HOST_MAC = "host.mac"                                    // MAC addresses :contentReference[oaicite:29]{index=29}
    const val HOST_ARCH = "host.arch"                                  // CPU architecture :contentReference[oaicite:30]{index=30}
    const val HOST_TYPE = "host.type"                                  // Host type (e.g., machine type) :contentReference[oaicite:31]{index=31}
    const val HOST_IMAGE_NAME = "host.image.name"                      // VM image/OS name :contentReference[oaicite:32]{index=32}
    const val HOST_IMAGE_ID = "host.image.id"                          // VM image ID :contentReference[oaicite:33]{index=33}
    const val HOST_IMAGE_VERSION = "host.image.version"                // Host image version :contentReference[oaicite:34]{index=34}

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

    // User & Identity Attributes
    const val USER_EMAIL = "user.email"                                // User email address
    const val USER_FULL_NAME = "user.full_name"                        // Full name of the user
    const val USER_HASH = "user.hash"                                  // Hashed user identifier for privacy
    const val USER_ID = "user.id"                                      // User identifier
    const val USER_NAME = "user.name"                                  // User's name or username
    const val USER_ROLES = "user.roles"                                // User roles

    // Session & Transaction Attributes
    const val SESSION_ID = "session.id"                                // Session identifier

    // Service Attributes
    const val SERVICE_INSTANCE_ID = "service.instance.id"              // Unique instance identifier
    const val SERVICE_NAME = "service.name"                            // Name of the service
    const val SERVICE_NAMESPACE = "service.namespace"                  // Namespace of the service (e.g., production, staging)
    const val SERVICE_VERSION = "service.version"                      // Version of the service

    // Deployment Attributes
    const val DEPLOYMENT_ENVIRONMENT_NAME = "deployment.environment.name" // Deployment environment (e.g., production, staging, dev)
    const val DEPLOYMENT_ID = "deployment.id"                          // Unique identifier of the deployment unit
    const val DEPLOYMENT_NAME = "deployment.name"                      // Deployment unit name
    const val DEPLOYMENT_STATUS = "deployment.status"                  // Current deployment status (e.g., starting, running, stopping, stopped)

    // Code Location Attributes
    const val CODE_FILE_PATH = "code.file.path"                        // Source file path
    const val CODE_FUNCTION_NAME = "code.function.name"                // Function or method name
    const val CODE_LINE_NUMBER = "code.line.number"                    // Line number in the source file
    const val CODE_STACKTRACE = "code.stacktrace"                      // Stack trace of the current function or method

    // Event & Message Attributes
    const val MESSAGING_CLIENT_ID = "messaging.client.id"                 // Client identifier must be unique for each instance of the client (e.g., JMS Connection ID)
    const val MESSAGING_DESTINATION_NAME = "messaging.destination.name"   // Destination of the message (e.g., queue name, topic name)
    const val MESSAGING_MESSAGE_BODY_SIZE = "messaging.message.body.size" // Size of the message body in bytes
    const val MESSAGING_MESSAGE_ID = "messaging.message.id"               // Message identifier
    const val MESSAGING_OPERATION_NAME = "messaging.operation.name"       // Operation name (e.g., send, receive, publish, subscribe)
    const val MESSAGING_OPERATION_TYPE = "messaging.operation.type"       // Operation type (e.g., request, response)
    const val MESSAGING_SYSTEM = "messaging.system"                       // Messaging system (e.g., kafka, rabbitmq, activemq)
    //@formatter:on
}
