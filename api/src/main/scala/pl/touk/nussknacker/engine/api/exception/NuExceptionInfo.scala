package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

case class NuExceptionInfo[T <: Throwable](componentInfo: Option[ExceptionComponentInfo], throwable: T, context: Context) extends Serializable

case class ExceptionComponentInfo(nodeId: String, name: String, typ: ComponentType)