package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.util.{ExceptionUtils, ReflectUtils}

import java.time.Instant

case class NuExceptionInfo(
    nodeComponentInfo: Option[NodeComponentInfo],
    throwable: Throwable,
    context: Context,
    // TODO: rename/describe what is the purpose of input
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
    // we unwrap common wrapping exceptions to return more precise information about cause of problem
    val unwrapped = ExceptionUtils.unwrapCommonWrappingExceptions(throwable)
    NuExceptionInfo(
      nodeComponentInfo,
      unwrapped,
      context,
      s"${ReflectUtils.simpleNameWithoutSuffix(unwrapped.getClass)}: ${unwrapped.getMessage}",
      Instant.now()
    )
  }

}
