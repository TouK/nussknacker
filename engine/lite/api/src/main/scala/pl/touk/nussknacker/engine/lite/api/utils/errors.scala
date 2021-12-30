package pl.touk.nussknacker.engine.lite.api.utils

import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.exception.{ExceptionComponentInfo, NuExceptionInfo}
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.customComponentTypes.CustomComponentContext

import scala.language.higherKinds
import scala.util.Try

object errors {

  def withErrors[F[_], T](customComponentContext: CustomComponentContext[F], componentName: String, componentType: ComponentType, ctx: Context)(action: => T): Either[ErrorType, T] = {
    Try(action).toEither.left.map(NuExceptionInfo(Some(ExceptionComponentInfo(customComponentContext.nodeId, componentName, componentType)), _, ctx))
  }

}
