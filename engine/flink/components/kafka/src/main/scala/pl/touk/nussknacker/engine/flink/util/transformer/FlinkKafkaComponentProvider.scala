package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, toFicusConfig}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSinkFactory
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.{
  FlinkKafkaAvroSinkImplFactory,
  FlinkKafkaUniversalSinkImplFactory
}
import pl.touk.nussknacker.engine.schemedkafka.sink.{
  KafkaAvroSinkFactory,
  KafkaAvroSinkFactoryWithEditor,
  UniversalKafkaSinkFactory
}
import pl.touk.nussknacker.engine.schemedkafka.source.{KafkaAvroSourceFactory, UniversalKafkaSourceFactory}
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkKafkaComponentProvider extends ComponentProvider {

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = UniversalSchemaRegistryClientFactory

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val overriddenDependencies = TemporaryKafkaConfigMapping.prepareDependencies(config, dependencies)
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._
    val avro                    = "DataSourcesAndSinks#schema-registry--avro-serialization"
    def universal(typ: String)  = s"DataSourcesAndSinks#kafka-$typ"
    val schemaRegistryTypedJson = "DataSourcesAndSinks#schema-registry--json-serialization"
    val noTypeInfo              = "DataSourcesAndSinks#no-type-information--json-serialization"

    val avroPayloadSerdeProvider = ConfluentSchemaBasedSerdeProvider.avroPayload(schemaRegistryClientFactory)
    val jsonPayloadSerdeProvider = ConfluentSchemaBasedSerdeProvider.jsonPayload(schemaRegistryClientFactory)
    val universalSerdeProvider   = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

    lazy val lowLevelKafkaComponents = List(
      ComponentDefinition("kafka-json", new GenericKafkaJsonSinkFactory(overriddenDependencies))
        .withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-json", new GenericJsonSourceFactory(overriddenDependencies))
        .withRelativeDocs(noTypeInfo),
      ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(overriddenDependencies))
        .withRelativeDocs("DataSourcesAndSinks#manually-typed--json-serialization"),
      ComponentDefinition(
        "kafka-avro",
        new KafkaAvroSourceFactory[Any, Any](
          schemaRegistryClientFactory,
          avroPayloadSerdeProvider,
          overriddenDependencies,
          new FlinkKafkaSourceImplFactory(None)
        )
      ).withRelativeDocs(avro),
      ComponentDefinition(
        "kafka-avro",
        new KafkaAvroSinkFactoryWithEditor(
          schemaRegistryClientFactory,
          avroPayloadSerdeProvider,
          overriddenDependencies,
          FlinkKafkaAvroSinkImplFactory
        )
      ).withRelativeDocs(avro),
      ComponentDefinition(
        "kafka-registry-typed-json",
        new KafkaAvroSourceFactory[Any, Any](
          schemaRegistryClientFactory,
          jsonPayloadSerdeProvider,
          overriddenDependencies,
          new FlinkKafkaSourceImplFactory(None)
        )
      ).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(
        "kafka-registry-typed-json",
        new KafkaAvroSinkFactoryWithEditor(
          schemaRegistryClientFactory,
          jsonPayloadSerdeProvider,
          overriddenDependencies,
          FlinkKafkaAvroSinkImplFactory
        )
      ).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(
        "kafka-registry-typed-json-raw",
        new KafkaAvroSinkFactory(
          schemaRegistryClientFactory,
          jsonPayloadSerdeProvider,
          overriddenDependencies,
          FlinkKafkaAvroSinkImplFactory
        )
      ).withRelativeDocs(schemaRegistryTypedJson),
      ComponentDefinition(
        "kafka-avro-raw",
        new KafkaAvroSinkFactory(
          schemaRegistryClientFactory,
          avroPayloadSerdeProvider,
          overriddenDependencies,
          FlinkKafkaAvroSinkImplFactory
        )
      ).withRelativeDocs(avro)
    )
    val universalKafkaComponents = List(
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSourceFactory[Any, Any](
          schemaRegistryClientFactory,
          universalSerdeProvider,
          overriddenDependencies,
          new FlinkKafkaSourceImplFactory(None)
        )
      ).withRelativeDocs(universal("source")),
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          overriddenDependencies,
          FlinkKafkaUniversalSinkImplFactory
        )
      ).withRelativeDocs(universal("sink"))
    )

    val lowLevelComponentsEnabled =
      config.getAs[Boolean]("config.lowLevelComponentsEnabled").getOrElse(KafkaConfig.lowLevelComponentsEnabled)
    if (lowLevelComponentsEnabled) {
      lowLevelKafkaComponents ::: universalKafkaComponents
    } else {
      universalKafkaComponents
    }
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false
}

//FIXME: Kafka components should not depend directly on ProcessObjectDependencies, only on
//appropriate config, this class is temporary solution, where we pass modified dependencies
private[transformer] object TemporaryKafkaConfigMapping {

  def prepareDependencies(config: Config, dependencies: ProcessObjectDependencies): ProcessObjectDependencies = {
    val kafkaConfig = config.getConfig("config")
    val kafkaConfigMergedWithGlobalConfig =
      dependencies.config.withValue(KafkaConfig.defaultGlobalKafkaConfigPath, fromAnyRef(kafkaConfig.root()))
    dependencies.copy(config = kafkaConfigMergedWithGlobalConfig)
  }

}
