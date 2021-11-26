package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context

case class NuExceptionInfo[T <: Throwable](nodeId: Option[String], throwable: T, context: Context) extends Serializable