package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming

// TODO: Rename to ComponentDependencies + rename config to modelConfig
case class ProcessObjectDependencies(config: Config, objectNaming: ObjectNaming) extends Serializable

object ProcessObjectDependencies {
  def empty: ProcessObjectDependencies = withConfig(ConfigFactory.empty())

  def withConfig(config: Config): ProcessObjectDependencies =
    ProcessObjectDependencies(config, ObjectNaming.OriginalNames)
}
