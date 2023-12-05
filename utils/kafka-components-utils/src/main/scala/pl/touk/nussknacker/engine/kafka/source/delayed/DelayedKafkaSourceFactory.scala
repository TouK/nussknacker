package pl.touk.nussknacker.engine.kafka.source.delayed

import cats.data.Validated.{Invalid, Valid}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.RecordFormatterFactory
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.{
  TypeDefinition,
  TypeDefinitionParamName,
  TypeParameter,
  calculateTypingResult
}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory._
import pl.touk.nussknacker.engine.util.TimestampUtils

import scala.reflect.ClassTag

class DelayedKafkaSourceFactory[K: ClassTag, V: ClassTag](
    deserializationSchemaFactory: KafkaDeserializationSchemaFactory[ConsumerRecord[K, V]],
    formatterFactory: RecordFormatterFactory,
    processObjectDependencies: ProcessObjectDependencies,
    implProvider: KafkaSourceImplFactory[K, V]
) extends KafkaSourceFactory[K, V](
      deserializationSchemaFactory,
      formatterFactory,
      processObjectDependencies,
      implProvider
    ) {

  override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(
    TypeParameter,
    TimestampFieldParameter,
    DelayParameter
  )

  override def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition = {
    case step @ TransformationStep(
          (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
          (TypeDefinitionParamName, DefinedEagerParameter(definition: TypeDefinition, _)) ::
          (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
          (DelayParameterName, DefinedEagerParameter(_, _)) :: Nil,
          _
        ) =>
      val topicValidationErrors = topicsValidationErrors(topic)
      calculateTypingResult(definition) match {
        case Valid((definition, typingResult)) =>
          val timestampValidationErrors =
            Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)).getOrElse(Nil)
          val errors = topicValidationErrors ++ timestampValidationErrors
          prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, errors)
        case Invalid(exc) =>
          val errors = topicValidationErrors ++ List(exc.toCustomNodeError(nodeId))
          prepareSourceFinalErrors(context, dependencies, step.parameters, errors = errors)
      }
    case step @ TransformationStep(
          (TopicParamName, _) :: (TypeDefinitionParamName, _) :: (TimestampFieldParamName, _) :: (
            DelayParameterName,
            _
          ) :: Nil,
          _
        ) =>
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

}

object DelayedKafkaSourceFactory {

  private final val delayValidators = List(MinimalNumberValidator(0), MaximalNumberValidator(Long.MaxValue))

  final val DelayParameterName = "delayInMillis"

  final val DelayParameter =
    Parameter.optional(DelayParameterName, Typed[java.lang.Long]).copy(validators = delayValidators)

  final val TimestampFieldParamName = "timestampField"

  // TODO: consider changing to lazy parameter and add the same parameter also in "not delayed" kafka sources
  final val TimestampFieldParameter = Parameter
    .optional(TimestampFieldParamName, Typed[String])
    .copy(
      editor = Some(DualParameterEditor(simpleEditor = StringParameterEditor, defaultMode = DualEditorMode.RAW))
    )

  def extractTimestampField(params: Map[String, Any]): String =
    params(TimestampFieldParamName).asInstanceOf[String]

  def extractDelayInMillis(params: Map[String, Any]): Long =
    params(DelayParameterName).asInstanceOf[Long]

  def validateTimestampField(field: String, typingResult: TypingResult)(
      implicit nodeId: NodeId
  ): List[ProcessCompilationError] = {
    typingResult match {
      case TypedObjectTypingResult(fields, _, _) =>
        fields.get(field) match {
          case Some(fieldTypingResult) if TimestampUtils.supportedTimestampTypes.contains(fieldTypingResult) =>
            List.empty
          case Some(fieldTypingResult) =>
            List(
              new CustomNodeError(
                nodeId.id,
                s"Field: '$field' has invalid type: ${fieldTypingResult.display}.",
                Some(TimestampFieldParamName)
              )
            )
          case None =>
            List(
              new CustomNodeError(
                nodeId.id,
                s"Field: '$field' doesn't exist in definition: ${fields.keys.mkString(", ")}.",
                Some(TimestampFieldParamName)
              )
            )
        }
      case _ =>
        throw new IllegalArgumentException(
          s"Not supported delayed source type definition: ${typingResult.getClass.getSimpleName}"
        )
    }
  }

}
