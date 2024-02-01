package pl.touk.nussknacker.engine.lite.api.utils

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.{ComponentId, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CustomComponentContext

import scala.language.higherKinds
import scala.util.Try

object errors {

  def withErrors[F[_], T](
      customComponentContext: CustomComponentContext[F],
      componentId: Option[ComponentId],
      ctx: Context
  )(action: => T): Either[ErrorType, T] = {
    Try(action).toEither.left
      .map(NuExceptionInfo(Some(NodeComponentInfo(customComponentContext.nodeId, componentId)), _, ctx))
  }

}
