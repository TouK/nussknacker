package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaBasedMessagesSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaBasedMessagesSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor, UniversalKafkaSinkFactory}
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, UniversalKafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkKafkaComponentProvider extends ComponentProvider {

  protected def schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory = CachedConfluentSchemaRegistryClientFactory

  protected def createAvroSchemaRegistryProvider: SchemaBasedMessagesSerdeProvider =
    ConfluentSchemaBasedMessagesSerdeProvider.avroPayload(schemaRegistryClientFactory)

  protected def createJsonSchemaRegistryProvider: SchemaBasedMessagesSerdeProvider =
    ConfluentSchemaBasedMessagesSerdeProvider.jsonPayload(schemaRegistryClientFactory)

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val overriddenDependencies = TemporaryKafkaConfigMapping.prepareDependencies(config, dependencies)
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._
    val avro = "DataSourcesAndSinks#schema-registry--avro-serialization"
    val schemaRegistryTypedJson = "DataSourcesAndSinks#schema-registry--json-serialization"
    val noTypeInfo = "DataSourcesAndSinks#no-type-information--json-serialization"

    val avroSerializingSchemaRegistryProvider = createAvroSchemaRegistryProvider
    val jsonSerializingSchemaRegistryProvider = createJsonSchemaRegistryProvider

    val lowLevelKafkaComponents = List(
      ComponentDefinition("kafka-json", new GenericKafkaJsonSinkFactory(overriddenDependencies)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-json", new GenericJsonSourceFactory(overriddenDependencies)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(overriddenDependencies)).withRelativeDocs("DataSourcesAndSinks#manually-typed--json-serialization"),
      ComponentDefinition("kafka-avro", new KafkaAvroSourceFactory(schemaRegistryClientFactory, avroSerializingSchemaRegistryProvider, overriddenDependencies, new FlinkKafkaSourceImplFactory(None))).withRelativeDocs(avro),
      ComponentDefinition("kafka-avro", new KafkaAvroSinkFactoryWithEditor(schemaRegistryClientFactory, avroSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(avro),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSourceFactory(schemaRegistryClientFactory, jsonSerializingSchemaRegistryProvider, overriddenDependencies, new FlinkKafkaSourceImplFactory(None))).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSinkFactoryWithEditor(schemaRegistryClientFactory, jsonSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json-raw", new KafkaAvroSinkFactory(schemaRegistryClientFactory, jsonSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-avro-raw", new KafkaAvroSinkFactory(schemaRegistryClientFactory, avroSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(avro)
    )

    // TODO: change link to the documentation when json schema handling will be available
    val universalKafkaComponents = List(
      ComponentDefinition("kafka", new UniversalKafkaSourceFactory(schemaRegistryClientFactory, avroSerializingSchemaRegistryProvider, overriddenDependencies, new FlinkKafkaSourceImplFactory(None))).withRelativeDocs(avro),
      ComponentDefinition("kafka", new UniversalKafkaSinkFactory(schemaRegistryClientFactory, avroSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(avro))

    lowLevelKafkaComponents ::: universalKafkaComponents
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false
}

//FIXME: Kafka components should not depend directly on ProcessObjectDependencies, only on
//appropriate config, this class is temporary solution, where we pass modified dependencies
private[transformer] object TemporaryKafkaConfigMapping {

  def prepareDependencies(config: Config, dependencies: ProcessObjectDependencies): ProcessObjectDependencies = {
    val kafkaConfig = config.getConfig("config")
    val kafkaConfigMergedWithGlobalConfig = dependencies.config.withValue(KafkaConfig.defaultGlobalKafkaConfigPath, fromAnyRef(kafkaConfig.root()))
    dependencies.copy(config = kafkaConfigMergedWithGlobalConfig)
  }

}

