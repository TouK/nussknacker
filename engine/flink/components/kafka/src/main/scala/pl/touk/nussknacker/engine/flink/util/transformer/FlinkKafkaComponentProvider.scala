package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  ComponentType,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.sink.flink.FlinkKafkaUniversalSinkImplFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

class FlinkKafkaComponentProvider extends ComponentProvider {

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = UniversalSchemaRegistryClientFactory

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val overriddenDependencies = TemporaryKafkaConfigMapping.prepareDependencies(config, dependencies)
    val docsConfig: DocsConfig = DocsConfig(config)
    import docsConfig._
    def universal(componentType: ComponentType) = s"DataSourcesAndSinks#kafka-$componentType"

    val universalSerdeProvider = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

    List(
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSourceFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          overriddenDependencies,
          new FlinkKafkaSourceImplFactory(None)
        )
      ).withRelativeDocs(universal(ComponentType.Source)),
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          overriddenDependencies,
          FlinkKafkaUniversalSinkImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Sink))
    )
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
