package pl.touk.nussknacker.engine.schemedkafka.source.delayed

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.schemaVersionParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory.PrecalculatedValueSchemaUniversalKafkaSourceFactoryState

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

  override def paramsDeterminedAfterSchema: List[Parameter] =
    super.paramsDeterminedAfterSchema ++ List(delayParameter.createParameter())

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    topicParamStep orElse
      schemaParamStep(Nil) orElse
      timestampFieldParamStep orElse
      validateTimestampFieldStep orElse
      nextSteps(context, dependencies)

  protected def validateTimestampFieldStep(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(_: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(_: String, _)) ::
          (`timestampFieldParamName`, DefinedEagerParameter(field, _)) :: Nil,
          state @ Some(PrecalculatedValueSchemaUniversalKafkaSourceFactoryState(valueValidationResult))
        ) =>
      val timestampValidation = valueValidationResult.toOption
        .map(_._2)
        .flatMap(typingResult => Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)))
        .getOrElse(Nil)

      NextParameters(
        paramsDeterminedAfterSchema,
        state = state,
        errors = timestampValidation
      )

    case TransformationStep(
          (`topicParamName`, _) :: (`schemaVersionParamName`, _) :: (`timestampFieldParamName`, _) :: Nil,
          _
        ) =>
      NextParameters(parameters = fallbackTimestampFieldParameter.createParameter() :: paramsDeterminedAfterSchema)
  }

  protected def timestampFieldParamStep(implicit nodeId: NodeId): ContextTransformationDefinition = {
    case TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (`schemaVersionParamName`, DefinedEagerParameter(version: String, _)) :: Nil,
          _
        ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(
        prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption),
        Some(schemaVersionParamName)
      )

      NextParameters(
        timestampFieldParameter(valueValidationResult.map(_._2).toOption).createParameter() :: Nil,
        state = Some(PrecalculatedValueSchemaUniversalKafkaSourceFactoryState(valueValidationResult))
      )
    case TransformationStep((`topicParamName`, _) :: (`schemaVersionParamName`, _) :: Nil, _) =>
      NextParameters(parameters = fallbackTimestampFieldParameter.createParameter() :: paramsDeterminedAfterSchema)
  }

}
