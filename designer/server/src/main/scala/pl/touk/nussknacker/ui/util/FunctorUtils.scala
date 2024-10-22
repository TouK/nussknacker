package pl.touk.nussknacker.ui.util

import cats.Functor
import cats.effect.IO
import cats.implicits.toFunctorOps
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime.syncIoRuntime

object FunctorUtils {

  implicit class Ops[M[_]: Functor, T](m: M[T]) {

    def onSuccessRunAsync(action: T => IO[Unit]): M[T] = {
      m.map { result =>
        action(result).unsafeRunAndForget()
        result
      }
    }

  }

}
