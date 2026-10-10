package org.snippetsearcher.permission.common

// el pedido esta mal armado: lo traduce a 400 el ApiExceptionHandler
class InvalidRequestException(
    message: String,
) : RuntimeException(message)
