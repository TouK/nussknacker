package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

// TODO: Rename to ModelDependencies + rename config to modelConfig
final case class ProcessObjectDependencies private (modelConfig: ModelConfig, namingStrategy: NamingStrategy)
    extends Serializable {
  def config: Config = modelConfig.underlyingConfig
}

object ProcessObjectDependencies {

  def apply(underlyingConfig: Config, namingStrategy: NamingStrategy): ProcessObjectDependencies = {
    new ProcessObjectDependencies(ModelConfig.parse(underlyingConfig), namingStrategy)
  }

  def withConfig(config: Config): ProcessObjectDependencies = {
    ProcessObjectDependencies(ModelConfig.parse(config), NamingStrategy.fromConfig(config))
  }

}
