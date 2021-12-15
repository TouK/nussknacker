package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor}
import pl.touk.nussknacker.engine.avro.source.KafkaAvroSourceFactory
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.BaseGenericTypedJsonSourceFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{deserializeToMap, deserializeToTypedMap, jsonFormatterFactory}
import pl.touk.nussknacker.engine.kafka.sink.{GenericJsonSerialization, KafkaSinkFactory}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig
import pl.touk.nussknacker.engine.util.config.DocsConfig.ComponentConfig

import scala.language.higherKinds

class LiteKafkaComponentProvider extends ComponentProvider {

  override def providerName: String = "kafka"

  protected lazy val avroSerializingSchemaRegistryProvider: SchemaRegistryProvider = createAvroSchemaRegistryProvider
  protected lazy val jsonSerializingSchemaRegistryProvider: SchemaRegistryProvider = createJsonSchemaRegistryProvider

  protected def createAvroSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider()

  protected def createJsonSchemaRegistryProvider: SchemaRegistryProvider = ConfluentSchemaRegistryProvider.jsonPayload(CachedConfluentSchemaRegistryClientFactory())

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    implicit val docsConfig: DocsConfig = new DocsConfig(config)
    val avro = "DataSourcesAndSinks#schema-registry--avro-serialization"
    val schemaRegistryTypedJson = "DataSourcesAndSinks#schema-registry--json-serialization"
    val noTypeInfo = "DataSourcesAndSinks#no-type-information--json-serialization"

    List(
      ComponentDefinition("kafka-json", new KafkaSinkFactory(GenericJsonSerialization(_), dependencies, LiteKafkaSinkImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-json", new KafkaSourceFactory[String, java.util.Map[_, _]](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToMap), jsonFormatterFactory, dependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-typed-json", new KafkaSourceFactory[String, TypedMap](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToTypedMap),
        jsonFormatterFactory, dependencies, new LiteKafkaSourceImplFactory
      ) with BaseGenericTypedJsonSourceFactory).withRelativeDocs("DataSourcesAndSinks#schema-registry--json-serialization"),
      ComponentDefinition("kafka-avro", new KafkaAvroSourceFactory(avroSerializingSchemaRegistryProvider, dependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(avro),
      ComponentDefinition("kafka-avro", new KafkaAvroSinkFactoryWithEditor(avroSerializingSchemaRegistryProvider, dependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(avro),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSourceFactory(jsonSerializingSchemaRegistryProvider, dependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json", new KafkaAvroSinkFactoryWithEditor(jsonSerializingSchemaRegistryProvider, dependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-registry-typed-json-raw", new KafkaAvroSinkFactory(jsonSerializingSchemaRegistryProvider, dependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition("kafka-avro-raw", new KafkaAvroSinkFactory(avroSerializingSchemaRegistryProvider, dependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(avro)
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
