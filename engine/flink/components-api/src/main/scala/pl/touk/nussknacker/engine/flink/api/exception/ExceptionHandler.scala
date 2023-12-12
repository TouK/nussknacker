package pl.touk.nussknacker.engine.flink.api.exception

import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.{Lifecycle, ScenarioProcessingContext}

trait ExceptionHandler extends Lifecycle {

  def handling[T](nodeComponentInfo: Option[NodeComponentInfo], context: ScenarioProcessingContext)(
      action: => T
  ): Option[T]

}
