package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy

// TODO: Rename to ModelDependencies + rename config to modelConfig
final case class ProcessObjectDependencies(config: Config, namingStrategy: NamingStrategy) extends Serializable
