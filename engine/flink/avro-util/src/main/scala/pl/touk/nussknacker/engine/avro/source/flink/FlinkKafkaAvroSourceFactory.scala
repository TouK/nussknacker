package pl.touk.nussknacker.engine.avro.source.flink

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source}
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.source.flink.ConsumerRecordBasedKafkaSource
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, PreparedKafkaTopic, RecordFormatter}

import scala.reflect.ClassTag

/**
  * Base implementation of KafkaSource factory with Avro schema support. It is based on GenericNodeTransformation to
  * - allow key and value type identification based on Schema Registry and
  * - allow Context initialization with event's value, key and metadata
  * You can provide schemas for both key and value. When useStringForKey = true (see KafkaConfig) the contents of event's key
  * are treated as String (this is default scenario).
  * Reader schema used in runtime is determined by topic and version.
  * Reader schema can be different than schema used by writer (e.g. when writer produces event with new schema), in that case "schema evolution" may be required.
  * For SpecificRecord use SpecificRecordKafkaAvroSourceFactory.
  * Assumptions:
  * 1. Every event that comes in has its key and value schemas registered in Schema Registry.
  * 2. Avro payload must include schema id for both Generic and Specific records (to provide "schema evolution" we need to know the exact writers schema).
  *
  * @param schemaRegistryProvider - provides a set of strategies for serialization and deserialization while event processing and/or testing.
  * @tparam K - type of event's key, used to determine if key object is Specific or Generic (for GenericRecords use Any)
  * @tparam V - type of event's value, used to determine if value object is Specific or Generic (for GenericRecords use Any)
  */
class FlinkKafkaAvroSourceFactory[K: ClassTag, V: ClassTag](val schemaRegistryProvider: SchemaRegistryProvider,
                                                            val processObjectDependencies: ProcessObjectDependencies,
                                                            protected val timestampAssigner: Option[TimestampWatermarkHandler[ConsumerRecord[K, V]]])
  extends KafkaAvroSourceFactory[K, V] {

  override protected def keyClassTag: ClassTag[K] = implicitly[ClassTag[K]]

  override protected def valueClassTag: ClassTag[V] = implicitly[ClassTag[V]]

  /**
    * Basic implementation of new source creation. Override this method to create custom KafkaSource.
    */
  override protected def createSource(params: Map[String, Any],
                                      dependencies: List[NodeDependencyValue],
                                      finalState: Option[State],
                                      preparedTopics: List[PreparedKafkaTopic],
                                      kafkaConfig: KafkaConfig,
                                      deserializationSchema: KafkaDeserializationSchema[ConsumerRecord[K, V]],
                                      formatter: RecordFormatter,
                                      flinkContextInitializer: ContextInitializer[ConsumerRecord[K, V]]): Source[ConsumerRecord[K, V]] =
    new ConsumerRecordBasedKafkaSource[K, V](preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter, flinkContextInitializer)

}
