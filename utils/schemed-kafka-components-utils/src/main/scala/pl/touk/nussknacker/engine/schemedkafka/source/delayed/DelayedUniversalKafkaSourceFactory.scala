package pl.touk.nussknacker.engine.schemedkafka.source.delayed

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaBasedSerdeProvider, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory

import scala.reflect.ClassTag

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
    TimestampFieldParameter,
    DelayParameter
  )

  override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition = {
    case step @ TransformationStep(
          (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
          (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
          (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
          (DelayParameterName, DefinedEagerParameter(delay, _)) :: Nil,
          _
        ) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(
        prepareUniversalValueSchemaDeterminer(preparedTopic, versionOption),
        Some(SchemaVersionParamName)
      )

      valueValidationResult match {
        case Valid((valueRuntimeSchema, typingResult)) =>
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

}
