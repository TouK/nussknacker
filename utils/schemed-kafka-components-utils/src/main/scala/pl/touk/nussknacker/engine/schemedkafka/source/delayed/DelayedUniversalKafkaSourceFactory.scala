package pl.touk.nussknacker.engine.schemedkafka.source.delayed

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import io.confluent.kafka.schemaregistry.ParsedSchema
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory.UniversalKafkaSourceFactoryState
import pl.touk.nussknacker.engine.schemedkafka.source.delayed.DelayedUniversalKafkaSourceFactory.DelayedUniversalKafkaSourceFactoryState

class DelayedUniversalKafkaSourceFactory(
    schemaRegistryClientFactory: SchemaRegistryClientFactory,
    schemaBasedMessagesSerdeProvider: SchemaBasedSerdeProvider,
    modelDependencies: ProcessObjectDependencies,
    implProvider: KafkaSourceImplFactory[Any, Any]
) extends UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      schemaBasedMessagesSerdeProvider,
      modelDependencies,
      implProvider
    ) {

  override def paramsDeterminedAfterSchema: List[Parameter] = super.paramsDeterminedAfterSchema ++ List(
    DelayParameter
  )

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition =
    topicParamStep orElse
      schemaParamStep(Nil) orElse
      timestampFieldParamStep orElse
      nextSteps(context, dependencies)

  override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
          (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
          (DelayParameterName, DefinedEagerParameter(delay, _)) :: Nil,
          Some(DelayedUniversalKafkaSourceFactoryState(valueValidationResult))
        ) =>
      val preparedTopic = prepareTopic(topic)

      valueValidationResult match {
        case Valid((_, typingResult)) =>
          val timestampValidationErrors =
            Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)).getOrElse(Nil)
          prepareSourceFinalResults(
            preparedTopic,
            valueValidationResult,
            context,
            dependencies,
            step.parameters,
            timestampValidationErrors
          )
        case Invalid(exc) =>
          prepareSourceFinalErrors(context, dependencies, step.parameters, List(exc))
      }
    case step @ TransformationStep(
          (`topicParamName`, _) :: (SchemaVersionParamName, _) :: (TimestampFieldParamName, _) :: (
            DelayParameterName,
            _
          ) :: Nil,
          _
        ) =>
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  protected def timestampFieldParamStep(implicit nodeId: NodeId): NodeTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) :: Nil,
          _
        ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(
        prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption),
        Some(SchemaVersionParamName)
      )

      NextParameters(
        timestampFieldParameter(valueValidationResult.map(_._2).toOption) :: paramsDeterminedAfterSchema,
        state = Some(DelayedUniversalKafkaSourceFactoryState(valueValidationResult))
      )
    case TransformationStep((topicParamName, _) :: (schemaVersionParamName, _) :: Nil, _) =>
      NextParameters(parameters = fallbackTimestampFieldParameter :: paramsDeterminedAfterSchema)
  }

}

object DelayedUniversalKafkaSourceFactory {

  case class DelayedUniversalKafkaSourceFactoryState(
      schemaValidationResults: Validated[
        ProcessCompilationError,
        (Option[RuntimeSchemaData[ParsedSchema]], typing.TypingResult)
      ]
  ) extends UniversalKafkaSourceFactoryState

}
