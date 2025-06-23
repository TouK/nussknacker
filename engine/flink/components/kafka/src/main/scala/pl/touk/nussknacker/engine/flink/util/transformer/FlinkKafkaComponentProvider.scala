package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  ComponentType,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
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

  override def create(componentProviderConfig: Config, modelConfig: ModelConfig): List[ComponentDefinition] = {
    val overriddenModelConfig = TemporaryKafkaConfigMapping.prepareModelConfig(componentProviderConfig, modelConfig)
    val finalModelConfig =
      modelConfigWithDisabledNamespacingIfApplicable(componentProviderConfig, overriddenModelConfig)
    val docsConfig = DocsConfig(componentProviderConfig)
    import docsConfig._
    def universal(componentType: ComponentType) = s"DataSourcesAndSinks#kafka-$componentType"

    val kafkaConfig = KafkaConfig.parseConfig(modelConfig.underlyingConfig)

    val universalSerdeProvider = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

    List(
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSourceFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          finalModelConfig,
          kafkaConfig,
          new FlinkKafkaSourceImplFactory(None)
        )
      ).withRelativeDocs(universal(ComponentType.Source)),
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          finalModelConfig,
          kafkaConfig,
          FlinkKafkaUniversalSinkImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Sink))
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

  private def modelConfigWithDisabledNamespacingIfApplicable(
      config: Config,
      modelConfig: ModelConfig
  ): ModelConfig = {
    val disableNamespacePath = "disableNamespace"
    if (config.hasPath(disableNamespacePath) && config.getBoolean(disableNamespacePath)) {
      modelConfig.copy(namingStrategy = NamingStrategy.Disabled)
    } else {
      modelConfig
    }
  }

}

//FIXME: Kafka components should not depend directly on ModelConfig, only on
//appropriate config, this class is temporary solution, where we pass modified dependencies
private[transformer] object TemporaryKafkaConfigMapping {

  def prepareModelConfig(config: Config, modelConfig: ModelConfig): ModelConfig = {
    val kafkaConfig = config.getConfig("config")
    modelConfig.transformUnderlyingConfig(
      _.withValue(KafkaConfig.DefaultGlobalKafkaConfigPath, fromAnyRef(kafkaConfig.root()))
    )
  }

}
