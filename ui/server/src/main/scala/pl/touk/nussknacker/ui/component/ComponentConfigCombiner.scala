package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig

object ComponentConfigCombiner {

  import cats.instances.map._
  import cats.syntax.semigroup._

  def combine(fixed: Map[String, SingleComponentConfig], dynamic: Map[String, SingleComponentConfig]): Map[String, SingleComponentConfig] = {
    fixed |+| dynamic
  }
}
