package pl.touk.nussknacker.test.utils.scalas

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait FutureExtensions {

  implicit class WaitFuture[T](f: Future[T]) {

    def waitForResult(implicit timeout: FiniteDuration = 1 second): T = Await.result(f, timeout)
  }

}

object FutureExtensions extends FutureExtensions
