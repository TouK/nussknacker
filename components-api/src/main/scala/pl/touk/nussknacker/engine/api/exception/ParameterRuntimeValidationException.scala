package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.parameter.ParameterName

case class ParameterRuntimeValidationException(
    paramName: ParameterName,
    override val input: String,
    message: String,
    cause: Throwable = null,
) extends NonTransientException(input, message, cause = cause)
