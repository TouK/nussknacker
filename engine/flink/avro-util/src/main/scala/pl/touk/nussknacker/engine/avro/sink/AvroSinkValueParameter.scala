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
    toSinkValueParameter(typing, paramName = None, isTopLevel = true)

  private def toSinkValueParameter(typing: TypingResult, paramName: Option[String], isTopLevel: Boolean)
                                  (implicit nodeId: NodeId): Validated[ProcessCompilationError, AvroSinkValueParameter] =
    typing match {
      case typed.typing.Unknown =>
        Invalid(CustomNodeError(nodeId.id, "Cannot determine typing for provided schema", None))

      case typedObject: TypedObjectTypingResult if containsRestrictedNames(typedObject) =>
        Invalid(CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None))

      /* kafka-avro-serializer does not support Array at top level
         [https://github.com/confluentinc/schema-registry/issues/1298] */
      case TypedClass(clazz, _) if isTopLevel && clazz == classOf[java.util.List[_]] =>
        Invalid(CustomNodeError(nodeId.id, "Unsupported Avro type. Top level Arrays are not supported", None))

      case typedObject: TypedObjectTypingResult =>
        val listOfValidatedFieldParams = typedObject.fields.map { case (fieldName, typing) =>
          val concatName = paramName.map(pn => s"$pn.$fieldName").getOrElse(fieldName)
          (fieldName, toSinkValueParameter(typing, Some(concatName), isTopLevel = false))
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
