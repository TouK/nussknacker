package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.util.ReflectUtils

import java.time.Instant

case class NuExceptionInfo(
    nodeComponentInfo: Option[NodeComponentInfo],
    throwable: Throwable,
    context: Context,
    input: String,
    timestamp: Instant
) extends Serializable

object NuExceptionInfo {

  def fromNonTransient(
      nodeComponentInfo: Option[NodeComponentInfo],
      nonTransient: NonTransientException,
      context: Context
  ): NuExceptionInfo = {
    NuExceptionInfo(
      nodeComponentInfo,
      nonTransient,
      context,
      nonTransient.input,
      nonTransient.timestamp
    )
  }

  def apply(
      nodeComponentInfo: Option[NodeComponentInfo],
      throwable: Throwable,
      context: Context
  ): NuExceptionInfo = {
    NuExceptionInfo(
      nodeComponentInfo,
      throwable,
      context,
      s"${ReflectUtils.simpleNameWithoutSuffix(throwable.getClass)}: ${throwable.getMessage}",
      Instant.now()
    )
  }

}
