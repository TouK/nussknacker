package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.Schema
import org.apache.avro.generic.GenericContainer
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.decode.BestEffortAvroDecoder
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{MockConfluentSchemaRegistryClientFactory, MockSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.{ConfluentSchemaRegistryProvider, ConfluentUtils}
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkImplFactory}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultCache}
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import scala.reflect.ClassTag

object LiteKafkaAvroTestScenarioRunner {
  private val DefaultAvroKafkaConfig = KafkaTestScenarioRunner.createConfig(
    ConfigFactory
      .empty()
      .withValue("kafka.kafkaProperties.\"schema.registry.url\"", fromAnyRef("schema-registry:666"))
  )

  def apply(sourceAvroImpl: KafkaSourceImplFactory[_, _], sinkAvroImpl: KafkaAvroSinkImplFactory): LiteKafkaAvroTestScenarioRunner =
    new LiteKafkaAvroTestScenarioRunner(sourceAvroImpl, sinkAvroImpl, Nil, DefaultAvroKafkaConfig)
}

class LiteKafkaAvroTestScenarioRunner(sourceAvroImpl: KafkaSourceImplFactory[_, _], sinkAvroImpl: KafkaAvroSinkImplFactory, components: List[ComponentDefinition], config: Config) extends TestScenarioRunner {

  import KafkaTestScenarioRunner._

  override type Input = KafkaInputType
  override type Output = KafkaOutputType

  private val schemasCache = new DefaultCache[String, Schema](cacheConfig = CacheConfig())
  private val dependencies = ProcessObjectDependencies(config, DefaultNamespacedObjectNaming)
  private val schemaRegistryMockClient: MockSchemaRegistryClient = new MockSchemaRegistryClient
  private val mockedSchemaProvider = ConfluentSchemaRegistryProvider.avroPayload(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
  private val avroEncoder: BestEffortAvroEncoder = BestEffortAvroEncoder(ValidationMode.strict)

  private lazy val delegate = {
    val sourceComponent = ComponentDefinition(SourceName, new KafkaAvroSourceFactory(mockedSchemaProvider, dependencies, sourceAvroImpl))
    val sinkComponent = ComponentDefinition(SinkName, new KafkaAvroSinkFactory(mockedSchemaProvider, dependencies, sinkAvroImpl))

    new KafkaTestScenarioRunner(sourceComponent :: sinkComponent :: components, config,
      valueSerializer = Some(new KafkaAvroSerializer(schemaRegistryMockClient).asInstanceOf[Serializer[Any]]),
      valueDeserializer = Some(new KafkaAvroDeserializer(schemaRegistryMockClient).asInstanceOf[Deserializer[Any]])
    )
  }

  override def runWithData[T<:Input:ClassTag, R<:Output](scenario: EspProcess, data: List[T]): List[R] = {
    val avroData = data.map(input => input.value() match {
      case _: GenericContainer => input
      case _ => convertToAvro(input)
    })

    delegate
      .runWithData(scenario, avroData)
  }

  def runWithResultValue[T<:Input:ClassTag](scenario: EspProcess, data: List[T]): List[Any] =
       runWithData[T, KafkaOutputType](scenario, data)
         .map(_.value())
         .map(BestEffortAvroDecoder.decode)

  private def convertToAvro(input: KafkaInputType) = {
    val subject = ConfluentUtils.topicSubject(input.topic, false)
    val schema = schemasCache.getOrCreate(subject) {
      val schemaString = schemaRegistryMockClient.getLatestSchemaMetadata(subject).getSchema
      AvroUtils.parseSchema(schemaString)
    }

    val value = avroEncoder.encodeOrError(input.value(), schema)
    input.withValue(value)
  }

  def registerSchema(topic: String, schema: Schema): Int = schemaRegistryMockClient.register(
    ConfluentUtils.topicSubject(topic, false),
    ConfluentUtils.convertToAvroSchema(schema)
  )

  def registerSchema(topic: String, schemaStr: String): Int =
    registerSchema(topic, AvroUtils.parseSchema(schemaStr))

}
