package pl.touk.nussknacker.ui.util

import cats.arrow.FunctionK
import cats.effect.IO
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import sttp.client3.SttpBackend
import sttp.client3.impl.cats.implicits.sttpBackendToCatsMappableSttpBackend
import sttp.monad.FutureMonad

import scala.concurrent.Future

object IOToFutureSttpBackendConverter {

  def convert(
      ioBackend: SttpBackend[IO, Any]
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): SttpBackend[Future, Any] = {
    import executionContextWithIORuntime.ioRuntime
    ioBackend.mapK(
      new FunctionK[IO, Future] {
        override def apply[A](io: IO[A]): Future[A] = io.unsafeToFuture()
      },
      new FunctionK[Future, IO] {
        override def apply[A](future: Future[A]): IO[A] = IO.fromFuture(IO.pure(future))
      }
    )(new FutureMonad)
  }

}
