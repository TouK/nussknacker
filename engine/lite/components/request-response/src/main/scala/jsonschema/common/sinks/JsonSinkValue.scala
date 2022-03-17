package jsonschema.common.sinks

import pl.touk.nussknacker.engine.api.LazyParameter

import scala.collection.immutable.ListMap

/*
* Whole file is almost one to one from AvroSinkValue - when moving to NU we could make AvroSinkValue generic / change name?
* */
object JsonSinkValue {
  case class InvalidSinkValue(parameterName: String)
    extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: JsonSinkValueParameter, parameterValues: Map[String, Any]): JsonSinkValue =
    sinkParameter match {
      case JsonSinkSingleValueParameter(param) =>
        val value = parameterValues(param.name)
        JsonSinkSingleValue(toLazyParameter(value, param.name))
      case JsonSinkRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        JsonSinkRecordValue(fields)
    }

  private def toLazyParameter(a: Any, paramName: String): LazyParameter[AnyRef] =
    try {
      a.asInstanceOf[LazyParameter[AnyRef]]
    } catch {
      case _: ClassCastException => throw InvalidSinkValue(paramName)
    }
}

/**
  *  Intermediate object which helps with mapping Json sink editor structure to Json message (see JsonSinkValueParameter)
  */
sealed trait JsonSinkValue

case class JsonSinkSingleValue(value: LazyParameter[AnyRef]) extends JsonSinkValue

case class JsonSinkRecordValue(fields: ListMap[String, JsonSinkValue]) extends JsonSinkValue

