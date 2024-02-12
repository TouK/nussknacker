package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategyProvider

object TestProcessObjectDependenciesProvider {

  def empty: ProcessObjectDependencies = withConfig(ConfigFactory.empty())

  def withConfig(modelConfig: Config): ProcessObjectDependencies =
    ProcessObjectDependencies(modelConfig, NamingStrategyProvider(modelConfig))

}
