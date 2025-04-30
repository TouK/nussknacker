package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.toFicusConfig
import net.ceedubs.ficus.readers.AnyValReaders._
import net.ceedubs.ficus.readers.OptionReader._
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies.ModelConfig

// TODO: Rename to ModelDependencies + rename config to modelConfig
final case class ProcessObjectDependencies private (modelConfig: ModelConfig, namingStrategy: NamingStrategy)
    extends Serializable {
  def config: Config = modelConfig.underlyingConfig
}

object ProcessObjectDependencies {

  def apply(underlyingConfig: Config, namingStrategy: NamingStrategy): ProcessObjectDependencies = {
    ProcessObjectDependencies(parseModelConfig(underlyingConfig), namingStrategy)
  }

  def withConfig(config: Config): ProcessObjectDependencies = {
    ProcessObjectDependencies(parseModelConfig(config), NamingStrategy.fromConfig(config))
  }

  def parseModelConfig(modelConfig: Config): ModelConfig = {
    ModelConfig(
      allowEndingScenarioWithoutSink = modelConfig.getOrElse[Boolean]("allowEndingScenarioWithoutSink", false),
      underlyingConfig = modelConfig,
    )
  }

  final case class ModelConfig(
      allowEndingScenarioWithoutSink: Boolean,
      // TODO: we should parse this underlying config as ModelConfig class fields instead of passing raw config
      underlyingConfig: Config,
  )

}
