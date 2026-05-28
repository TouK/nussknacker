package pl.touk.nussknacker.engine.api.exception

case class ParameterRuntimeValidationException(
    paramName: pl.touk.nussknacker.engine.api.parameter.ParameterName,
    override val input: String,
    message: String,
    cause: Throwable = null,
) extends NonTransientException(input, message, cause = cause)
