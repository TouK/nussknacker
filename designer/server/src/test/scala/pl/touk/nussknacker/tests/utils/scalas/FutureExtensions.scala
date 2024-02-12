package pl.touk.nussknacker.tests.utils.scalas

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait FutureExtensions {

  implicit class WaitFuture[T](f: Future[T]) {

    def result(implicit timeout: FiniteDuration = 1 second): T = Await.result(f, timeout)
  }

}

object FutureExtensions extends FutureExtensions
