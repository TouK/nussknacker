package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed
import pl.touk.nussknacker.engine.api.typed.typing.{TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter.FieldName
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer

private[sink] case object AvroSinkValueParameter {

  type FieldName = String

  private val restrictedParamNames = Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  def apply(typing: TypingResult)(implicit nodeId: NodeId): Validated[ProcessCompilationError, AvroSinkValueParameter] =
    toSinkValueParameter(typing, paramName = None)

  private def toSinkValueParameter(typing: TypingResult, paramName: Option[String])(implicit nodeId: NodeId): Validated[ProcessCompilationError, AvroSinkValueParameter] =
    typing match {
      case typed.typing.Unknown =>
        Invalid(CustomNodeError(nodeId.id, "Cannot determine typing for provided schema", None))

      case typedObject: TypedObjectTypingResult if containsRestrictedNames(typedObject) =>
        Invalid(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None))

      case TypedClass(clazz, _) if clazz == classOf[java.util.List[_]] =>
        Invalid(unsupportedTypeError)

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

  private def containsRestrictedNames(obj: TypedObjectTypingResult): Boolean =
    (obj.fields.keySet & restrictedParamNames).nonEmpty

  private def sequence(l: List[(FieldName, Validated[ProcessCompilationError, AvroSinkValueParameter])])
  : Validated[ProcessCompilationError, List[(FieldName, AvroSinkValueParameter)]] = {
    val zero: Validated[ProcessCompilationError, List[(FieldName, AvroSinkValueParameter)]] = Valid(Nil)
    l.foldLeft(zero) {
      case (aggValidated, (fieldName, validatedField)) =>
        aggValidated.andThen { parameters =>
          validatedField.map { parameter =>
            (fieldName, parameter) :: parameters
          }
        }
    }
  }

  private def unsupportedTypeError(implicit nodeId: NodeId): CustomNodeError =
    CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None)
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
