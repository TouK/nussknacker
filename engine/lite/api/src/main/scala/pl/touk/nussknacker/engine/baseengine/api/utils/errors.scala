package pl.touk.nussknacker.engine.baseengine.api.utils

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.baseengine.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.baseengine.api.customComponentTypes.CustomComponentContext

import scala.language.higherKinds
import scala.util.Try

object errors {

  def withErrors[F[_], T](customComponentContext: CustomComponentContext[F], ctx: Context)(action: => T): Either[ErrorType, T] = {
    Try(action).toEither.left.map(NuExceptionInfo(Some(customComponentContext.nodeId), _, ctx))
  }

}
