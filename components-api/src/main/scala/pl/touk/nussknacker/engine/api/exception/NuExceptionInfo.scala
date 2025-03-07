package pl.touk.nussknacker.engine.api.exception

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.util.ReflectUtils

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

  // TODO: Maybe we should unwrap some common wrapping exceptions such as ExecutionException, CompletionException, InvocationTargetException
  //       to provide more detailed information about cause of problem?
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
