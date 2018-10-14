package jdbcat.ktor.example

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CORS
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.StatusPages
import io.ktor.features.callIdMdc
import io.ktor.http.HttpMethod
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.util.toMap
import jdbcat.ktor.example.route.v1.departmentRoute
import jdbcat.ktor.example.route.v1.employeeRoute
import jdbcat.ktor.example.route.v1.healthCheckRoute
import jdbcat.ktor.example.route.v1.reportRoute
import mu.KotlinLogging
import java.util.UUID

// All REST calls must specify version, e.g. http://localhost/api/v1/healthcheck
const val serviceApiVersionV1 = "api/v1"

private val logger = KotlinLogging.logger { }

/**
 * Application's ENTRY POINT.
 * This method is called by Ktor and this entry point is configured in /resources/application.conf
 */
@Suppress("unused")
fun Application.main() {

    // Initial bootstrap
    // Performs initial initialization of dependency injection, database tables etc.
    bootstrap()

    // Produce default headers
    install(DefaultHeaders)

    // ktor 0.9.5 added MDC support for coroutines and this allows us to print call request id for the entire
    // execution context. This is great, because we can return that call request id back to a client
    // in a header and in case of error, user can provide us with a call request id (let's say we might
    // print it on a screen or in JavaScript console) and we could track the entire execution path even
    // in a very busy logs (e.g. on file system or in Splunk).
    // In order to print call request id we use %X{mdc-callid} specifier in resources/logback.xml
    install(CallLogging) {
        callIdMdc("mdc-callid")
    }
    install(CallId) {
        // Unique id will be generated in form of "callid-UUID" for a CallLogging feature described above
        generate {
            "callid-${UUID.randomUUID()}"
        }
    }

    // Some frameworks such as Angular require additional CORS configuration
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        header("*")
        allowCredentials = true
        allowSameOrigin = true
        anyHost()
    }

    // Content conversions - here we setup serialization and deserialization of JSON objects
    install(ContentNegotiation) {
        // We use Jackson for JSON: https://github.com/FasterXML/jackson
        jackson {
            // Use ISO-8601 date/time format
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Pretty print of JSON output
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    // Return proper HTTP error: https://ktor.io/features/status-pages.html
    install(StatusPages) {
        // setup exception handlers
        // We have introduced application-specific exception - AppGenericException allowing us
        // to specify HTTP status code to be returned to HTTP REST client.
        exception<AppGenericException> { ex ->
            logger.error(ex) { "Application exception to be returned to a caller" }
            call.respond(ex.httpStatusCode, ex.toResponse())
        }
    }

    // Setup REST routing
    routing {

        // Print REST requests into a log
        trace {
            application.log.debug(it.buildText())
            application.log.debug(it.call.request.headers.toMap().toString())
        }

        route("/$serviceApiVersionV1") {
            // api/v1/healthcheck
            healthCheckRoute()
            // api/v1/departments
            departmentRoute()
            // api/v1/employees
            employeeRoute()
            // api/v1/reports
            reportRoute()
        }
    }
}