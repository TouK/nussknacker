package pl.touk.nussknacker.engine.kafka.generic

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import KafkaTypedSourceFactory._

trait BaseGenericTypedJsonSourceFactory extends KafkaSourceFactory[String, TypedMap] {

  override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(
    TypeParameter
  )

  override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case step@TransformationStep((TopicParamName, DefinedEagerParameter(topic: String, _)) ::
      (TypeDefinitionParamName, DefinedEagerParameter(definition: Any, _)) :: Nil, _) =>
      val topicValidationErrors = topicsValidationErrors(topic)
      calculateTypingResult(definition) match {
        case Valid((_, typingResult)) =>
          prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, topicValidationErrors)
        case Invalid(exc) =>
          val errors = topicValidationErrors ++ List(exc.toCustomNodeError(nodeId))
          prepareSourceFinalErrors(context, dependencies, step.parameters, errors)
      }
    case step@TransformationStep((TopicParamName, top) :: (TypeDefinitionParamName, typ) :: Nil, _) =>
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

}
