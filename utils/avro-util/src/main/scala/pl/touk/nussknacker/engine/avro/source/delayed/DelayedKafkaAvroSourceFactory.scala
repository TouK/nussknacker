package pl.touk.nussknacker.engine.avro.source.delayed

import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory._

import scala.reflect.ClassTag

class DelayedKafkaAvroSourceFactory[K: ClassTag, V: ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                              processObjectDependencies: ProcessObjectDependencies,
                                                              implProvider: KafkaSourceImplFactory[K, V])
  extends KafkaAvroSourceFactory[K, V](schemaRegistryProvider, processObjectDependencies, implProvider) {

  override def paramsDeterminedAfterSchema: List[Parameter] = super.paramsDeterminedAfterSchema ++ List(
    TimestampFieldParameter, DelayParameter
  )

  override protected def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                  (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step@TransformationStep(
    (`topicParamName`, DefinedEagerParameter(topic: String, _)) ::
      (SchemaVersionParamName, DefinedEagerParameter(version: String, _)) ::
      (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
      (DelayParameterName, DefinedEagerParameter(delay, _)) :: Nil, _) =>
      val preparedTopic = prepareTopic(topic)
      val versionOption = parseVersionOption(version)
      val valueValidationResult = determineSchemaAndType(prepareValueSchemaDeterminer(preparedTopic, versionOption), Some(SchemaVersionParamName))

      valueValidationResult match {
        case Valid((valueRuntimeSchema, typingResult)) =>
          val delayValidationErrors = Option(delay.asInstanceOf[java.lang.Long]).map(d => validateDelay(d)).getOrElse(Nil)
          val timestampValidationErrors = Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)).getOrElse(Nil)
          val errors = delayValidationErrors ++ timestampValidationErrors
          prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, errors)
        case Invalid(exc) =>
          prepareSourceFinalErrors(context, dependencies, step.parameters, List(exc))
      }
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (TimestampFieldParamName, _) :: (DelayParameterName, _) :: Nil, _) =>
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

}