@file:Suppress("unused")

package io.github.smyrgeorge.log4k.impl

/**
 * [OpenTelemetryAttributes] holds constants for the standard OpenTelemetry semantic-convention
 * attribute keys. Attach them to spans, logs, and metrics to describe exceptions, errors, HTTP and
 * URL traffic, network endpoints, databases (including transactions), messaging systems, hosts,
 * services, users, sessions, deployments, and source-code locations.
 *
 * Every key below is validated against the OpenTelemetry Semantic Conventions registry, and each
 * comment notes the attribute's stability level (Stable / Development). Deprecated keys (such as the
 * legacy `db.system`, `db.name`, `db.statement`) are intentionally omitted in favour of their
 * current replacements.
 *
 * Attribute registry index:
 * https://opentelemetry.io/docs/specs/semconv/registry/attributes/
 */
object OpenTelemetryAttributes {
    //@formatter:off

    // Exception Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/exception/
    const val EXCEPTION = "exception"                        // Span event name used when recording an exception (Stable) — see the exceptions spec
    const val EXCEPTION_MESSAGE = "exception.message"        // The exception message (Stable)
    const val EXCEPTION_STACKTRACE = "exception.stacktrace"  // Stacktrace as a string in the natural representation for the language runtime (Stable)
    const val EXCEPTION_TYPE = "exception.type"              // The type of the exception, i.e. its fully-qualified class name (Stable)

    // Error Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/error/
    const val ERROR_TYPE = "error.type"  // Describes a class of error the operation ended with; keep low cardinality (Stable)

    // HTTP Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/
    const val HTTP_REQUEST_METHOD = "http.request.method"                    // HTTP request method, e.g. GET, POST, HEAD (Stable)
    const val HTTP_REQUEST_METHOD_ORIGINAL = "http.request.method_original"  // Original HTTP method sent by the client in the request line (Stable)
    const val HTTP_REQUEST_RESEND_COUNT = "http.request.resend_count"        // Ordinal number of the request resend attempt, for retries (Stable)
    const val HTTP_REQUEST_BODY_SIZE = "http.request.body.size"              // Size of the request payload body in bytes (Development)
    const val HTTP_REQUEST_SIZE = "http.request.size"                        // Total size of the request in bytes, including headers (Development)
    const val HTTP_REQUEST_HEADER = "http.request.header"                    // Prefix for request headers; append the normalized header name, e.g. http.request.header.content-type (Stable)
    const val HTTP_RESPONSE_STATUS_CODE = "http.response.status_code"        // HTTP response status code (Stable)
    const val HTTP_RESPONSE_BODY_SIZE = "http.response.body.size"            // Size of the response payload body in bytes (Development)
    const val HTTP_RESPONSE_SIZE = "http.response.size"                      // Total size of the response in bytes, including headers (Development)
    const val HTTP_RESPONSE_HEADER = "http.response.header"                  // Prefix for response headers; append the normalized header name, e.g. http.response.header.content-type (Stable)
    const val HTTP_ROUTE = "http.route"                                      // Matched route template, e.g. /users/:id (Stable)
    const val HTTP_CONNECTION_STATE = "http.connection.state"                // State of the HTTP connection in the connection pool (Development)

    // URL Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/url/
    const val URL_FULL = "url.full"                            // Absolute URL describing a network resource per RFC3986 (Stable)
    const val URL_SCHEME = "url.scheme"                        // The URI scheme identifying the protocol, e.g. http, https (Stable)
    const val URL_PATH = "url.path"                            // The URI path component (Stable)
    const val URL_QUERY = "url.query"                          // The URI query component (Stable)
    const val URL_FRAGMENT = "url.fragment"                    // The URI fragment component, the part after # (Stable)
    const val URL_PORT = "url.port"                            // Port extracted from url.full (Development)
    const val URL_DOMAIN = "url.domain"                        // Domain extracted from url.full (Development)
    const val URL_ORIGINAL = "url.original"                    // Unmodified original URL as seen in the event source (Development)
    const val URL_TEMPLATE = "url.template"                    // Low-cardinality template of an absolute path reference (Development)
    const val URL_EXTENSION = "url.extension"                  // File extension extracted from the path, excluding the leading dot (Development)
    const val URL_REGISTERED_DOMAIN = "url.registered_domain"  // Highest registered domain, stripped of the subdomain, e.g. example.com (Development)
    const val URL_SUBDOMAIN = "url.subdomain"                  // Subdomain portion of a fully qualified domain name (Development)
    const val URL_TOP_LEVEL_DOMAIN = "url.top_level_domain"    // The effective top-level domain (eTLD), e.g. com, co.uk (Development)

    // Network Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/network/
    const val NETWORK_TYPE = "network.type"                          // OSI network layer or non-OSI equivalent, e.g. ipv4, ipv6 (Stable)
    const val NETWORK_TRANSPORT = "network.transport"                // OSI transport layer or IPC method, e.g. tcp, udp, pipe (Stable)
    const val NETWORK_PROTOCOL_NAME = "network.protocol.name"        // OSI application layer or non-OSI equivalent, e.g. http, amqp (Stable)
    const val NETWORK_PROTOCOL_VERSION = "network.protocol.version"  // Actual version of the protocol used for network communication (Stable)
    const val NETWORK_PEER_ADDRESS = "network.peer.address"          // Peer address of the connection: IP address or Unix domain socket name (Stable)
    const val NETWORK_PEER_PORT = "network.peer.port"                // Peer port number of the network connection (Stable)
    const val NETWORK_LOCAL_ADDRESS = "network.local.address"        // Local address of the connection: IP address or Unix domain socket name (Stable)
    const val NETWORK_LOCAL_PORT = "network.local.port"              // Local port number of the network connection (Stable)

    // Server Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/server/
    const val SERVER_ADDRESS = "server.address"  // Server domain name, IP address, or Unix domain socket name (Stable)
    const val SERVER_PORT = "server.port"        // Server port number (Stable)

    // Client Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/client/
    const val CLIENT_ADDRESS = "client.address"  // Client domain name, IP address, or Unix domain socket name (Stable)
    const val CLIENT_PORT = "client.port"        // Client port number (Stable)

    // Database Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/db/
    const val DB_SYSTEM_NAME = "db.system.name"                                  // DBMS product identified by the client, e.g. postgresql, mysql, sqlite (Stable)
    const val DB_NAMESPACE = "db.namespace"                                      // Name of the database, fully qualified within the server address and port (Stable)
    const val DB_COLLECTION_NAME = "db.collection.name"                          // Name of a collection (table, container) within the database (Stable)
    const val DB_QUERY_TEXT = "db.query.text"                                    // The database query being executed, e.g. the SQL statement (Stable)
    const val DB_QUERY_SUMMARY = "db.query.summary"                              // Low-cardinality summary of the query text (Stable)
    const val DB_QUERY_PARAMETER = "db.query.parameter"                          // Prefix for query placeholder parameters; append the parameter key (Development)
    const val DB_RESPONSE_STATUS_CODE = "db.response.status_code"                // Database response status or error code, as a string (Stable)
    const val DB_RESPONSE_RETURNED_ROWS = "db.response.returned_rows"            // Number of rows returned by the operation (Development)
    const val DB_CLIENT_CONNECTION_POOL_NAME = "db.client.connection.pool.name"  // Name of the connection pool, unique within the application (Development)
    const val DB_CLIENT_CONNECTION_STATE = "db.client.connection.state"          // State of the pooled connection, e.g. idle, used (Development)

    // Database Transaction & Operation Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/db/
    // OpenTelemetry has no dedicated `db.transaction.*` keys. Transactions are modeled through
    // `db.operation.name` with values such as BEGIN, COMMIT, ROLLBACK, SAVEPOINT, and multi-statement
    // transactions/batches through `db.operation.batch.size`. See the database spans spec for details:
    // https://opentelemetry.io/docs/specs/semconv/database/database-spans/
    const val DB_OPERATION_NAME = "db.operation.name"                // Operation or command being executed, e.g. SELECT, INSERT, BEGIN, COMMIT, ROLLBACK (Stable)
    const val DB_OPERATION_BATCH_SIZE = "db.operation.batch.size"    // Number of queries included in a batch (or transactional multi-statement) request (Stable)
    const val DB_STORED_PROCEDURE_NAME = "db.stored_procedure.name"  // Name of the stored procedure being executed (Stable)

    // Messaging Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/messaging/
    const val MESSAGING_SYSTEM = "messaging.system"                                      // Messaging system identified by the client, e.g. kafka, rabbitmq, activemq (Development)
    const val MESSAGING_OPERATION_NAME = "messaging.operation.name"                      // System-specific name of the messaging operation, e.g. publish, ack (Development)
    const val MESSAGING_OPERATION_TYPE = "messaging.operation.type"                      // Type of the messaging operation, e.g. send, receive, process, settle (Development)
    const val MESSAGING_DESTINATION_NAME = "messaging.destination.name"                  // Message destination name, e.g. the queue or topic name (Development)
    const val MESSAGING_DESTINATION_PARTITION_ID = "messaging.destination.partition.id"  // Identifier of the partition messages are sent to or received from (Development)
    const val MESSAGING_MESSAGE_ID = "messaging.message.id"                              // Identifier for the message, assigned by the messaging system (Development)
    const val MESSAGING_MESSAGE_BODY_SIZE = "messaging.message.body.size"                // Size of the message body in bytes (Development)
    const val MESSAGING_MESSAGE_CONVERSATION_ID = "messaging.message.conversation_id"    // Conversation/correlation ID identifying the conversation the message belongs to (Development)
    const val MESSAGING_BATCH_MESSAGE_COUNT = "messaging.batch.message_count"            // Number of messages sent, received, or processed in a batch operation (Development)
    const val MESSAGING_CLIENT_ID = "messaging.client.id"                                // Unique identifier for the client that consumes or produces messages (Development)
    const val MESSAGING_CONSUMER_GROUP_NAME = "messaging.consumer.group.name"            // Name of the consumer group the consumer is associated with (Development)

    // Thread Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/thread/
    const val THREAD_ID = "thread.id"      // Current managed thread ID, as opposed to the OS thread ID (Development)
    const val THREAD_NAME = "thread.name"  // Current thread name (Development)

    // Code Location Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/code/
    const val CODE_FILE_PATH = "code.file.path"          // Source code file path that identifies the code unit (Stable)
    const val CODE_FUNCTION_NAME = "code.function.name"  // Method or function fully-qualified name, without arguments (Stable)
    const val CODE_LINE_NUMBER = "code.line.number"      // Line number in code.file.path best representing the operation (Stable)
    const val CODE_COLUMN_NUMBER = "code.column.number"  // Column number in code.file.path best representing the operation (Stable)
    const val CODE_STACKTRACE = "code.stacktrace"        // Stacktrace as a string in the natural representation for the language runtime (Stable)

    // User & Identity Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/user/
    const val USER_ID = "user.id"                // Unique identifier of the user (Development)
    const val USER_NAME = "user.name"            // Short name or login/username of the user (Development)
    const val USER_FULL_NAME = "user.full_name"  // Full name of the user (Development)
    const val USER_EMAIL = "user.email"          // Email address of the user (Development)
    const val USER_HASH = "user.hash"            // Unique user hash to correlate information for a user in anonymized form (Development)
    const val USER_ROLES = "user.roles"          // Array of user roles at the time of the event (Development)

    // Session Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/session/
    const val SESSION_ID = "session.id"                    // Unique identifier of the session (Development)
    const val SESSION_PREVIOUS_ID = "session.previous_id"  // Previous session.id for this user, when known (Development)

    // Service Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/service/
    const val SERVICE_NAME = "service.name"                      // Logical name of the service (Stable)
    const val SERVICE_NAMESPACE = "service.namespace"            // A namespace for service.name to group related services (Stable)
    const val SERVICE_VERSION = "service.version"                // Version string of the service (Stable)
    const val SERVICE_INSTANCE_ID = "service.instance.id"        // Unique string ID of this service instance (Stable)
    const val SERVICE_PEER_NAME = "service.peer.name"            // Logical name of the service on the other side of the connection (Development)
    const val SERVICE_PEER_NAMESPACE = "service.peer.namespace"  // Logical namespace of the service on the other side of the connection (Development)

    // Deployment Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/deployment/
    const val DEPLOYMENT_ENVIRONMENT_NAME = "deployment.environment.name"  // Deployment environment / tier, e.g. production, staging, development (Stable)
    const val DEPLOYMENT_ID = "deployment.id"                              // Unique identifier of the deployment (Development)
    const val DEPLOYMENT_NAME = "deployment.name"                          // Name of the deployment (Development)
    const val DEPLOYMENT_STATUS = "deployment.status"                      // Whether the deployment succeeded or failed (Development)

    // Host Resource Attributes
    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/host/
    const val HOST_ID = "host.id"                          // Unique host identifier: cloud instance ID, or machine-id otherwise (Development)
    const val HOST_NAME = "host.name"                      // Name of the host, from the hostname command or the FQDN (Development)
    const val HOST_TYPE = "host.type"                      // Type of host; for cloud environments this is the machine type (Development)
    const val HOST_ARCH = "host.arch"                      // CPU architecture the host system runs on, e.g. amd64, arm64 (Development)
    const val HOST_IP = "host.ip"                          // Available IP addresses of the host, excluding loopback interfaces (Development)
    const val HOST_MAC = "host.mac"                        // Available MAC addresses of the host, excluding loopback interfaces (Development)
    const val HOST_IMAGE_NAME = "host.image.name"          // Name of the VM image or OS install the host was provisioned from (Development)
    const val HOST_IMAGE_ID = "host.image.id"              // VM image ID or host OS image ID (Development)
    const val HOST_IMAGE_VERSION = "host.image.version"    // Version string of the VM image or host OS (Development)
    const val HOST_CPU_VENDOR_ID = "host.cpu.vendor.id"    // Processor manufacturer identifier, e.g. GenuineIntel (Development)
    const val HOST_CPU_MODEL_NAME = "host.cpu.model.name"  // Processor model designation (Development)
    //@formatter:on
}
