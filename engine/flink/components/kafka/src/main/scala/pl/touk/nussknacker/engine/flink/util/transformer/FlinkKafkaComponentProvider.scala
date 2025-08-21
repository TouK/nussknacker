package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.schemedkafka.BaseFlinkKafkaComponentProvider

class FlinkKafkaComponentProvider extends BaseFlinkKafkaComponentProvider with ComponentProvider {

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = {
    createComponents(
      componentProviderConfig,
      componentDependencies.modelConfig.namingStrategy,
      componentDependencies.modelConfig.jsonLikeValuesEnteringMode
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}
