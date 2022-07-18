package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, toFicusConfig}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.sink.{KafkaAvroSinkFactory, KafkaAvroSinkFactoryWithEditor, UniversalKafkaSinkFactory}
import pl.touk.nussknacker.engine.avro.source.{KafkaAvroSourceFactory, UniversalKafkaSourceFactory}
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.BaseGenericTypedJsonSourceFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{deserializeToMap, deserializeToTypedMap, jsonFormatterFactory}
import pl.touk.nussknacker.engine.kafka.sink.{GenericJsonSerialization, KafkaSinkFactory}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

import java.util
import scala.language.higherKinds

object LiteKafkaComponentProvider {
  val KafkaUniversalName = "kafka"
  val KafkaJsonName = "kafka-json"
  val KafkaTypedJsonName = "kafka-typed-json"
  val KafkaAvroName = "kafka-avro"
  val KafkaRegistryTypedJsonName = "kafka-registry-typed-json"
  val KafkaSinkRegistryTypedRawJsonName = "kafka-registry-typed-json-raw"
  val KafkaSinkRawAvroName = "kafka-avro-raw"
}

class LiteKafkaComponentProvider(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends ComponentProvider {

  import LiteKafkaComponentProvider._

  def this() = this(CachedConfluentSchemaRegistryClientFactory)

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val overriddenDependencies = TemporaryKafkaConfigMapping.prepareDependencies(config, dependencies)
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._
    val avro = "DataSourcesAndSinks#schema-registry--avro-serialization"
    val schemaRegistryTypedJson = "DataSourcesAndSinks#schema-registry--json-serialization"
    val noTypeInfo = "DataSourcesAndSinks#no-type-information--json-serialization"

    val avroPayloadSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(schemaRegistryClientFactory)
    val jsonPayloadSerdeProvider = ConfluentSchemaBasedSerdeProvider.jsonPayload(schemaRegistryClientFactory)
    val universalSerdeProvider = ConfluentSchemaBasedSerdeProvider.universal(schemaRegistryClientFactory)

    lazy val lowLevelKafkaComponents = List(
      ComponentDefinition(KafkaJsonName, new KafkaSinkFactory(GenericJsonSerialization(_), overriddenDependencies, LiteKafkaSinkImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition(KafkaJsonName, new KafkaSourceFactory[String, util.Map[_, _]](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToMap), jsonFormatterFactory, overriddenDependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition(KafkaTypedJsonName, new KafkaSourceFactory[String, TypedMap](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToTypedMap),
        jsonFormatterFactory, overriddenDependencies, new LiteKafkaSourceImplFactory
      ) with BaseGenericTypedJsonSourceFactory).withRelativeDocs("DataSourcesAndSinks#manually-typed--json-serialization"),
      ComponentDefinition(KafkaAvroName, new KafkaAvroSourceFactory(schemaRegistryClientFactory, avroPayloadSerdeProvider, overriddenDependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(avro),
      ComponentDefinition(KafkaAvroName, new KafkaAvroSinkFactoryWithEditor(schemaRegistryClientFactory, avroPayloadSerdeProvider, overriddenDependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(avro),
      ComponentDefinition(KafkaRegistryTypedJsonName, new KafkaAvroSourceFactory(schemaRegistryClientFactory, jsonPayloadSerdeProvider, overriddenDependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(KafkaRegistryTypedJsonName, new KafkaAvroSinkFactoryWithEditor(schemaRegistryClientFactory, jsonPayloadSerdeProvider, overriddenDependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(KafkaSinkRegistryTypedRawJsonName, new KafkaAvroSinkFactory(schemaRegistryClientFactory, jsonPayloadSerdeProvider, overriddenDependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(KafkaSinkRawAvroName, new KafkaAvroSinkFactory(schemaRegistryClientFactory, avroPayloadSerdeProvider, overriddenDependencies, LiteKafkaAvroSinkImplFactory)).withRelativeDocs(avro))

    // TODO: change link to the documentation when json schema handling will be available
    val universalKafkaComponents = List(
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSourceFactory(schemaRegistryClientFactory, universalSerdeProvider, overriddenDependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(avro),
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSinkFactory(schemaRegistryClientFactory, universalSerdeProvider, overriddenDependencies, LiteKafkaUniversalSinkImplFactory)).withRelativeDocs(avro))

    val shouldAddLowLevelKafkaComponents = config.getAs[Boolean]("config.addLowLevelKafkaComponents").getOrElse(true)
    if (shouldAddLowLevelKafkaComponents) {
      lowLevelKafkaComponents ::: universalKafkaComponents
    } else {
      universalKafkaComponents
    }
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}

//FIXME: Kafka components should not depend directly on ProcessObjectDependencies, only on
//appropriate config, this class is temporary solution, where we pass modified dependencies
private[components] object TemporaryKafkaConfigMapping {

  def prepareDependencies(config: Config, dependencies: ProcessObjectDependencies): ProcessObjectDependencies = {
    val kafkaConfig = config.getConfig("config")
    val kafkaConfigMergedWithGlobalConfig = dependencies.config.withValue(KafkaConfig.defaultGlobalKafkaConfigPath, fromAnyRef(kafkaConfig.root()))
    dependencies.copy(config = kafkaConfigMergedWithGlobalConfig)
  }

}