package pl.touk.nussknacker.engine.avro.sink

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter.FieldName
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer

private[sink] case object AvroSinkValueParameter {

  type FieldName = String

  val restrictedParamNames: Set[FieldName] =
    Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  def apply(typing: TypingResult)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, AvroSinkValueParameter] =
    toSinkValueParameter(typing, paramName = None)

  private def toSinkValueParameter(typing: TypingResult, paramName: Option[String])
                                  (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, AvroSinkValueParameter] =
    typing match {
      /* TODO: Since GenericNodeTransformation#implementation passes all parameters in a single Map we need to restrict value parameter names,
               so they do not collide with other parameters like Topic or Key. */
      case typedObject: TypedObjectTypingResult if containsRestrictedNames(typedObject) =>
        Invalid(NonEmptyList.one(
          CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None)))

      case typedObject: TypedObjectTypingResult =>
        val listOfValidatedFieldParams = typedObject.fields.map { case (fieldName, typing) =>
          val concatName = paramName.map(pn => s"$pn.$fieldName").getOrElse(fieldName)
          (fieldName, toSinkValueParameter(typing, Some(concatName)))
        }.toList
        sequence(listOfValidatedFieldParams).map(l => AvroSinkRecordParameter(l.toMap))

      case _ =>
        val parameter = Parameter(paramName.getOrElse(SinkValueParamName), typing)
          .copy(
            isLazyParameter = true,
            editor = new ParameterTypeEditorDeterminer(typing).determine())
        Valid(AvroSinkPrimitiveValueParameter(parameter))
    }

  private def containsRestrictedNames(obj: TypedObjectTypingResult): Boolean = {
    val fieldNames = obj.fields.keySet
    fieldNames.nonEmpty & (fieldNames & restrictedParamNames).nonEmpty
  }

  private def sequence(l: List[(FieldName, ValidatedNel[ProcessCompilationError, AvroSinkValueParameter])])
  : ValidatedNel[ProcessCompilationError, List[(FieldName, AvroSinkValueParameter)]] = {
    import cats.implicits.{toTraverseOps, catsStdInstancesForList}
    l.map { case (fieldName, validated) =>
      validated.map(sinkValueParam => (fieldName, sinkValueParam))
    }.sequence
  }
}

private[sink] sealed trait AvroSinkValueParameter {

  def toParameters: List[Parameter] = this match {
    case AvroSinkPrimitiveValueParameter(value) => value :: Nil
    case AvroSinkRecordParameter(fields) => fields.toList.flatMap(_._2.toParameters)
  }
}

private[sink] case class AvroSinkPrimitiveValueParameter(value: Parameter)
  extends AvroSinkValueParameter

private[sink] case class AvroSinkRecordParameter(fields: Map[FieldName, AvroSinkValueParameter])
  extends AvroSinkValueParameter
