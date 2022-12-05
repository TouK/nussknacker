package pl.touk.nussknacker.engine.util.sinkvalue

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData._

object SinkValue {
  case class InvalidSinkValue(parameterName: String)
    extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: SinkValueParameter, parameterValues: Map[String, Any]): SinkValue =
    sinkParameter match {
      case SinkSingleValueParameter(param, _) =>
        val value = parameterValues(param.name)
        SinkSingleValue(toLazyParameter(value, param.name))

      case SinkRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        SinkRecordValue(fields)
    }

  private def toLazyParameter(a: Any, paramName: String): LazyParameter[AnyRef] =
    try {
      a.asInstanceOf[LazyParameter[AnyRef]]
    } catch {
      case _: ClassCastException => throw InvalidSinkValue(paramName)
    }
}