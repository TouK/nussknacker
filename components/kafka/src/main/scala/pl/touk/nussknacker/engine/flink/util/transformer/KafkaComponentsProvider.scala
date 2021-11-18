package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.flink.FlinkKafkaAvroSinkFactory
import pl.touk.nussknacker.engine.avro.sink.{BaseKafkaAvroSinkFactory, BaseKafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.flink.FlinkKafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}

class KafkaComponentsProvider extends ComponentProvider {

  protected val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()

  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
    ComponentDefinition("kafka-json", new GenericKafkaJsonSinkFactory(dependencies)),
    ComponentDefinition("kafka-json", new GenericJsonSourceFactory(dependencies)),
    ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(dependencies)),
    ComponentDefinition("kafka-avro", new FlinkKafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, dependencies, None)),
    ComponentDefinition("kafka-avro", new BaseKafkaAvroSinkFactoryWithEditor(avroSerializingSchemaRegistryProvider, dependencies) with FlinkKafkaAvroSinkFactory),
    ComponentDefinition("kafka-registry-typed-json", new FlinkKafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, dependencies, None)),
    ComponentDefinition("kafka-registry-typed-json", new BaseKafkaAvroSinkFactoryWithEditor(jsonSerializingSchemaRegistryProvider, dependencies) with FlinkKafkaAvroSinkFactory),
    ComponentDefinition("kafka-registry-typed-json-raw", new BaseKafkaAvroSinkFactory(jsonSerializingSchemaRegistryProvider, dependencies) with FlinkKafkaAvroSinkFactory),
    ComponentDefinition("kafka-avro-raw", new BaseKafkaAvroSinkFactory(avroSerializingSchemaRegistryProvider, dependencies) with FlinkKafkaAvroSinkFactory),
  )

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
