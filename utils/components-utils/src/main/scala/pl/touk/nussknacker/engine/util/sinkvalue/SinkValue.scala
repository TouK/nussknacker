package pl.touk.nussknacker.engine.util.sinkvalue

import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData._
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedRecordParameter, SingleSchemaBasedParameter}

object SinkValue {
  case class InvalidSinkValue(parameterName: String)
      extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: SchemaBasedParameter, parameterValues: Params): SinkValue =
    sinkParameter match {
      case SingleSchemaBasedParameter(param, _) =>
        val value = parameterValues.extractUnsafe(param.name)
        SinkSingleValue(toLazyParameter(value, param.name))

      case SchemaBasedRecordParameter(paramFields) =>
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
