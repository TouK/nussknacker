package pl.touk.nussknacker.engine.flink.api.exception

import pl.touk.nussknacker.engine.api.exception.ExceptionComponentInfo
import pl.touk.nussknacker.engine.api.{Context, Lifecycle}

trait ExceptionHandler extends Lifecycle {

  def handling[T](componentInfo: Option[ExceptionComponentInfo], context: Context)(action: => T): Option[T]
}
