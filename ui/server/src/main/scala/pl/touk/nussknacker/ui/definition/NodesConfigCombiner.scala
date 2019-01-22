package pl.touk.nussknacker.ui.definition

import pl.touk.nussknacker.engine.api.process.SingleNodeConfig

object NodesConfigCombiner {
  import cats.instances.map._
  import cats.syntax.semigroup._

  def combine(fixed: Map[String, SingleNodeConfig], dynamic: Map[String, SingleNodeConfig]): Map[String, SingleNodeConfig] = {
    fixed |+| dynamic
  }
}
