package pl.touk.nussknacker.engine.lite.api.utils

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.{ComponentInfo, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CustomComponentContext

import scala.language.higherKinds
import scala.util.Try

object errors {

  def withErrors[F[_], T](customComponentContext: CustomComponentContext[F], componentInfo: Option[ComponentInfo], ctx: Context)(action: => T): Either[ErrorType, T] = {
    Try(action).toEither.left.map(NuExceptionInfo(Some(NodeComponentInfo(customComponentContext.nodeId, componentInfo)), _, ctx))
  }

}
