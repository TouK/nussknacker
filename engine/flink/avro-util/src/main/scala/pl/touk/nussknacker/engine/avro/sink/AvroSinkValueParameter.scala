package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer

private[sink] case object AvroSinkValueParameter {

  private val restrictedParamNames = Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  def apply(typing: TypingResult)(implicit nodeId: NodeId): Validated[ProcessCompilationError, AvroSinkValueParameter] =
    toSinkValueParam(typing, isRoot = true)

  private def toSinkValueParam(typing: TypingResult, isRoot: Boolean)(implicit nodeId: NodeId): Validated[ProcessCompilationError, AvroSinkValueParameter] =
    typing match {
      case typed.typing.Unknown =>
        Invalid(CustomNodeError(nodeId.id, "Cannot determine typing for provided schema", None))

      case typedObject: TypedObjectTypingResult if containsRestrictedNames(typedObject) =>
        Invalid(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None))

      // FIXME: fragile
      case TypedClass(clazz, _) if clazz == classOf[java.util.List[_]] =>
        Invalid(CustomNodeError(nodeId.id, "Unsupported Avro type. Supported types are null, Boolean, Integer, Long, Float, Double, String, byte[] and IndexedRecord", None))

      case typedObject: TypedObjectTypingResult if isRoot =>
        Valid(toRecord(typedObject))

      case _: TypedObjectTypingResult =>
        Valid(toSingle(SinkValueParamName, typing))

      case _ =>
        Valid(toSingle(SinkValueParamName, typing))
    }

  private def toSingle(name: String, typing: TypingResult): AvroSinkSingleValueParameter = {
    val parameter = Parameter(name, typing).copy(
      isLazyParameter = true,
      // TODO
      editor = new ParameterTypeEditorDeterminer(typing).determine())
    AvroSinkSingleValueParameter(parameter)
  }

  private def toRecord(typedObject: TypedObjectTypingResult): AvroSinkRecordParameter = {
    val fields = typedObject.fields.map { case (name, typing) =>
      toSingle(name, typing)
    }.toList
    AvroSinkRecordParameter(fields)
  }


  private def containsRestrictedNames(obj: TypedObjectTypingResult): Boolean =
    (obj.fields.keySet & restrictedParamNames).nonEmpty
}

private[sink] sealed trait AvroSinkValueParameter {

  def toParameters: List[Parameter] = this match {
    case AvroSinkSingleValueParameter(value) => value :: Nil
    case AvroSinkRecordParameter(fields) => fields.map(_.value)
    case AvroSinkValueEmptyParameter => Nil
  }
}

private[sink] case class AvroSinkSingleValueParameter(value: Parameter) extends AvroSinkValueParameter

private[sink] case class AvroSinkRecordParameter(fields: List[AvroSinkSingleValueParameter]) extends AvroSinkValueParameter

private[sink] case object AvroSinkValueEmptyParameter extends AvroSinkValueParameter
