package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming

// TODO: Rename to ComponentDependencies + rename config to modelConfig
case class ProcessObjectDependencies(config: Config, objectNaming: ObjectNaming) extends Serializable
