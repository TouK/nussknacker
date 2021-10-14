package pl.touk.nussknacker.engine.avro.sink.flink

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}

object AvroSinkValue {
  case class InvalidSinkValue(parameterName: String)
    extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: AvroSinkValueParameter, parameterValues: Map[String, Any]): AvroSinkValue =
    sinkParameter match {
      case AvroSinkSingleValueParameter(param) =>
        val value = parameterValues(param.name)
        AvroSinkSingleValue(toLazyParameter(value, param.name), param.typ)

      case AvroSinkRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        AvroSinkRecordValue(fields)
    }

  private def toLazyParameter(a: Any, paramName: String): LazyParameter[AnyRef] =
    try {
      a.asInstanceOf[LazyParameter[AnyRef]]
    } catch {
      case _: ClassCastException => throw InvalidSinkValue(paramName)
    }
}

/*
  Intermediate object which helps with mapping Avro sink editor structure to Avro message (see AvroSinkValueParameter)
 */
sealed trait AvroSinkValue {

  def typingResult: TypingResult
}

case class AvroSinkSingleValue(value: LazyParameter[AnyRef], typingResult: TypingResult)
  extends AvroSinkValue

case class AvroSinkRecordValue(fields: List[(String, AvroSinkValue)])
  extends AvroSinkValue {

  val typingResult: TypedObjectTypingResult = {
    val fieldsTyping = fields.map { case (fieldName, value) => (fieldName, value.typingResult)}
    TypedObjectTypingResult(fieldsTyping)
  }
}
