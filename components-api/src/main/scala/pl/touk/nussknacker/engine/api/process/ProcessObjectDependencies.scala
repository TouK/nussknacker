package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

// TODO: Rename to ModelDependencies + rename config to modelConfig
final case class ProcessObjectDependencies(config: Config, namingStrategy: NamingStrategy) extends Serializable

object ProcessObjectDependencies {
  // TODO: move these to some test utils since they shouldn't be used like this outside of tests
  def empty: ProcessObjectDependencies = withConfig(ConfigFactory.empty())

  def withConfig(config: Config): ProcessObjectDependencies =
    ProcessObjectDependencies(config, NamingStrategy(None))
}
