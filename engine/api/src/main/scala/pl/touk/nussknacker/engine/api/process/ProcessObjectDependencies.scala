package pl.touk.nussknacker.engine.api.process

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming

case class ProcessObjectDependencies(config: Config,
                                     objectNaming: ObjectNaming) extends Serializable
