package pl.touk.nussknacker.engine.flink.api.exception

import pl.touk.nussknacker.engine.api.{Context, Lifecycle}
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo

trait ExceptionHandler extends Lifecycle {

  def handling[T](nodeComponentInfo: Option[NodeComponentInfo], context: Context)(
      action: => T
  ): Option[T]

}
