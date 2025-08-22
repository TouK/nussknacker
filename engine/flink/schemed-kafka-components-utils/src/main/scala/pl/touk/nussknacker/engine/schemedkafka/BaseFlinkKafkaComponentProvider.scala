package pl.touk.nussknacker.engine.schemedkafka

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelConfig.JsonLikeValuesEnteringMode
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentType}
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
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

trait BaseFlinkKafkaComponentProvider {

  protected def schemaRegistryClientFactory: SchemaRegistryClientFactory = UniversalSchemaRegistryClientFactory

  def createComponents(
      componentProviderConfig: Config,
      modelNamingStrategy: NamingStrategy,
      jsonLikeValuesEnteringMode: JsonLikeValuesEnteringMode
  ): List[ComponentDefinition] = {
    val kafkaComponentsConfig = KafkaComponentsConfig.parseConfigNestedAtConfigKey(componentProviderConfig)
    val namingStrategy = namingStrategyWithDisabledNamespacingIfApplicable(componentProviderConfig, modelNamingStrategy)
    val universalSerdeProvider =
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory, kafkaComponentsConfig)

    val docsConfig = DocsConfig(componentProviderConfig)
    import docsConfig._
    def universal(componentType: ComponentType) = s"DataSourcesAndSinks#kafka-$componentType"

    List(
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSourceFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          kafkaComponentsConfig,
          namingStrategy,
          new FlinkKafkaSourceImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Source)),
      ComponentDefinition(
        "kafka",
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          kafkaComponentsConfig,
          namingStrategy,
          jsonLikeValuesEnteringMode,
          FlinkKafkaUniversalSinkImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Sink))
    )
  }

  // Currently, all kafka components config is nested inside "config" key, disableNamespace is the only config
  // property on the top level. TODO: unify it
  private def namingStrategyWithDisabledNamespacingIfApplicable(
      componentProviderConfig: Config,
      modelNamingStrategy: NamingStrategy
  ): NamingStrategy = {
    val disableNamespacePath = "disableNamespace"
    if (componentProviderConfig
        .hasPath(disableNamespacePath) && componentProviderConfig.getBoolean(disableNamespacePath)) {
      NamingStrategy.Disabled
    } else {
      modelNamingStrategy
    }
  }

}
