package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.ScenarioProcessingContext
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo

case class NuExceptionInfo[T <: Throwable](
    nodeComponentInfo: Option[NodeComponentInfo],
    throwable: T,
    context: ScenarioProcessingContext
) extends Serializable
