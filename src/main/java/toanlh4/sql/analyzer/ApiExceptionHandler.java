package toanlh4.sql.analyzer;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions thrown by REST controllers into a JSON body carrying the
 * exception message, so API clients can show what went wrong.
 *
 * @author toanlh4
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(
            String message,
            String exception) {
    }

    // Invalid SQL or invalid request input -> client error.
    @ExceptionHandler({
        SqlParseException.class,
        ValidationException.class,
        RelConversionException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        LOGGER.warn("Bad request on {}: {}", request.getRequestURI(), ex.toString());
        return build(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ApiError> handleSqlException(SQLException ex, HttpServletRequest request) {
        LOGGER.error("Database error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest request) {
        // Keep the status of Spring MVC's own exceptions (404, 405, 415, ...).
        HttpStatusCode status = ex instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) {
            LOGGER.error("Unhandled error on {}", request.getRequestURI(), ex);
        } else {
            LOGGER.warn("Request failed on {}: {}", request.getRequestURI(), ex.toString());
        }
        return build(status, ex);
    }

    private static ResponseEntity<ApiError> build(HttpStatusCode status, Exception ex) {
        ApiError body = new ApiError(messageOf(ex), ex.getClass().getName());
        return ResponseEntity.status(status).body(body);
    }

    // Calcite often wraps the useful message in a cause; fall back down the chain.
    private static String messageOf(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                return t.getMessage();
            }
        }
        return ex.getClass().getSimpleName();
    }
}
