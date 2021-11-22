package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus.toFicusConfig
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory

class FlinkKafkaComponentProvider extends ComponentProvider {

  protected val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()

  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val kafkaConfig = config.getConfig("config")
    val kafkaConfigMergedWithGlobalConfig = dependencies.config.withValue("kafka", fromAnyRef(kafkaConfig.root()))
    List(
      ComponentDefinition("kafka-json", new GenericKafkaJsonSinkFactory(dependencies.copy(config = kafkaConfigMergedWithGlobalConfig))),
      ComponentDefinition("kafka-json", new GenericJsonSourceFactory(dependencies.copy(config = kafkaConfigMergedWithGlobalConfig))),
      ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(dependencies.copy(config = kafkaConfigMergedWithGlobalConfig))),
      ComponentDefinition("kafka-avro", new KafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), new FlinkKafkaSourceImplFactory(None))),
      ComponentDefinition("kafka-avro", new KafkaAvroSinkFactoryWithEditor(avroSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), FlinkKafkaAvroSinkImplFactory)),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), new FlinkKafkaSourceImplFactory(None))),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSinkFactoryWithEditor(jsonSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), FlinkKafkaAvroSinkImplFactory)),
      ComponentDefinition("kafka-registry-typed-json-raw", new KafkaAvroSinkFactory(jsonSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), FlinkKafkaAvroSinkImplFactory)),
      ComponentDefinition("kafka-avro-raw", new KafkaAvroSinkFactory(avroSerializingSchemaRegistryProvider, dependencies.copy(config = kafkaConfigMergedWithGlobalConfig), FlinkKafkaAvroSinkImplFactory)),
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false
}
