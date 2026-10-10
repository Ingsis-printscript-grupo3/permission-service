package org.snippetsearcher.permission.common

import org.snippetsearcher.permission.permission.InvalidRequestException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

// traduce los errores del pedido a un 400 con un mensaje, en un solo lugar para todos los controllers
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(InvalidRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidRequest(e: InvalidRequestException): ErrorResponse = ErrorResponse(e.message ?: "Pedido inválido")

    // el body no se pudo leer: falta un campo o el type no es OWNER ni READ
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unreadableBody(e: HttpMessageNotReadableException): ErrorResponse =
        ErrorResponse("El body no es válido: tiene que traer resourceType, resourceId, userId y type (OWNER o READ)")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun missingParameter(e: MissingServletRequestParameterException): ErrorResponse =
        ErrorResponse("Falta el parámetro '${e.parameterName}'")

    // un parametro que no se pudo convertir, como un relation que no es OWNED, SHARED ni ALL
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidParameter(e: MethodArgumentTypeMismatchException): ErrorResponse {
        val allowed = e.requiredType?.enumConstants?.joinToString(", ")
        val hint = if (allowed != null) ": tiene que ser uno de $allowed" else ""
        return ErrorResponse("El parámetro '${e.name}' no es válido$hint")
    }
}

data class ErrorResponse(
    val error: String,
)
