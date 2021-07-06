package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated.{Invalid, Valid}
import org.apache.avro.generic.GenericRecord
import org.apache.flink.streaming.connectors.kafka.KafkaDeserializationSchema
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.SchemaVersionParamName
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.flink.api.process.FlinkContextInitializer
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.generic.BaseKafkaDelayedSourceFactory
import pl.touk.nussknacker.engine.kafka.generic.KafkaDelayedSourceFactory._
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.TypeDefinitionParamName
import pl.touk.nussknacker.engine.kafka.source.KafkaSource
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

class DelayedKafkaAvroSourceFactory[K:ClassTag, V:ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                            processObjectDependencies: ProcessObjectDependencies,
                                                            timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends KafkaAvroSourceFactory[K, V](schemaRegistryProvider, processObjectDependencies, timestampAssigner)
    with BaseKafkaDelayedSourceFactory {

  override def paramsDeterminedAfterSchema: List[Parameter] = super.paramsDeterminedAfterSchema ++ List(
    TimestampParameter, DelayParameter
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
        case Valid((valueRuntimeSchema, _)) =>
          val definition = valueRuntimeSchema.map(_.schema.getFields.asScala.map(f => (f.name(), ())).toMap ).getOrElse(Map.empty).asJava
          val delayValidationErrors = Option(delay.asInstanceOf[java.lang.Long]).map(d => validateDelay(d)).getOrElse(Nil)
          val timestampValidationErrors = Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, definition)).getOrElse(Nil)
          val errors = delayValidationErrors ++ timestampValidationErrors
          prepareSourceFinalResults(preparedTopic, valueValidationResult, context, dependencies, step.parameters, errors)
        case Invalid(exc) =>
          prepareSourceFinalErrors(context, dependencies, step.parameters, List(exc))
      }
    case step@TransformationStep((`topicParamName`, _) :: (SchemaVersionParamName, _) :: (TypeDefinitionParamName, _) :: (TimestampFieldParamName, _) :: (DelayParameterName, _) :: Nil, _) =>
      prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
  }

  override protected def createSource(params: Map[String, Any],
                                      dependencies: List[NodeDependencyValue],
                                      finalState: Option[State],
                                      preparedTopics: List[PreparedKafkaTopic],
                                      kafkaConfig: KafkaConfig,
                                      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                      timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]],
                                      formatter: RecordFormatter,
                                      flinkContextInitializer: FlinkContextInitializer[ConsumerRecord[K, V]]): KafkaSource[ConsumerRecord[K, V]] = {
    val timestampFieldName = extractTimestampField(params)
    val extractTimestamp: (ConsumerRecord[K, V], Long) => Long = (consumerRecord, kafkaEventTimestamp) => {
      Option(timestampFieldName)
        .map(f => consumerRecord.value().asInstanceOf[GenericRecord].get(f).asInstanceOf[Long])
        .getOrElse(kafkaEventTimestamp)
    }
    extractDelayInMillis(params) match {
      case millis if millis > 0 =>
        createDelayedKafkaSource[K, V](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer, millis, extractTimestamp)
      case _ =>
        super.createSource(params, dependencies, finalState, preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)
    }
  }
}
