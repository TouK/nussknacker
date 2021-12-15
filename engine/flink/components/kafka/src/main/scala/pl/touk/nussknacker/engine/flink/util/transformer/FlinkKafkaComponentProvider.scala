package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.Ficus.toFicusConfig
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkImplFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, TemporaryKafkaConfigMapping}
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.engine.util.config.DocsConfig.ComponentConfig

class FlinkKafkaComponentProvider extends ComponentProvider {

  protected val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()

  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val overriddenDependencies = TemporaryKafkaConfigMapping.prepareDependencies(config, dependencies)
    implicit val docsConfig: DocsConfig = new DocsConfig(config)
    val avro = "DataSourcesAndSinks#schema-registry--avro-serialization"
    val schemaRegistryTypedJson = "DataSourcesAndSinks#schema-registry--json-serialization"
    val noTypeInfo = "DataSourcesAndSinks#no-type-information--json-serialization"
    List(
      ComponentDefinition("kafka-json", new GenericKafkaJsonSinkFactory(overriddenDependencies)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-json", new GenericJsonSourceFactory(overriddenDependencies)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(overriddenDependencies)).withRelativeDocs("DataSourcesAndSinks#schema-registry--json-serialization"),
      ComponentDefinition("kafka-avro", new KafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, overriddenDependencies, new FlinkKafkaSourceImplFactory(None))).withRelativeDocs(avro),
      ComponentDefinition("kafka-avro", new KafkaAvroSinkFactoryWithEditor(avroSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(avro),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, overriddenDependencies, new FlinkKafkaSourceImplFactory(None))).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSinkFactoryWithEditor(jsonSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json-raw", new KafkaAvroSinkFactory(jsonSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-avro-raw", new KafkaAvroSinkFactory(avroSerializingSchemaRegistryProvider, overriddenDependencies, FlinkKafkaAvroSinkImplFactory)).withRelativeDocs(avro)
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false
}
