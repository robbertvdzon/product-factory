package nl.vdzon.productfactory.support

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

data class ApiError(val status: Int, val message: String, val timestamp: Instant = Instant.now())

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(error: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiError(HttpStatus.BAD_REQUEST.value(), error.message ?: "Ongeldige invoer"))
}
