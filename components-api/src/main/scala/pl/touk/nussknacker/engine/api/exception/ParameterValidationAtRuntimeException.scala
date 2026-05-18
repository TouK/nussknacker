package pl.touk.nussknacker.engine.api.exception

class ParameterValidationAtRuntimeException(input: String, message: String, cause: Throwable = null)
    extends NonTransientException(input, message, cause = cause)
