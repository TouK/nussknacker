package pl.touk.nussknacker.engine.util.sinkvalue

import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.util.parameters.{SchemaBasedRecordParameter, SingleSchemaBasedParameter}
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData._

import scala.util.Try

object SinkValue {
  final case class InvalidSinkValue(parameterName: String)
      extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: SchemaBasedParameter, parameterValues: Params): SinkValue =
    sinkParameter match {
      case SingleSchemaBasedParameter(param, _) =>
        val value = Try(parameterValues.extractUnsafe[LazyParameter[AnyRef]](param.name))
          .getOrElse(throw InvalidSinkValue(param.name.value))
        SinkSingleValue(value)
      case SchemaBasedRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        SinkRecordValue(fields)
    }

}
