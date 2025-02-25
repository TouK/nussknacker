package pl.touk.nussknacker.ui.util

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object FutureUtils {

  implicit class FutureOps[T](future: Future[T]) {

    // Limits Future completion to some timeout, after which timeoutResult suppress result of given future.
    // This solution is based on: https://stackoverflow.com/a/42468372/1370301
    def withTimeout(duration: FiniteDuration, timeoutResult: => T)(
        implicit actorSystem: ActorSystem
    ): Future[LimitedByTimeoutResult[T]] = {
      import actorSystem._
      Future.firstCompletedOf(
        Seq(
          org.apache.pekko.pattern.after(duration)(Future.successful(CompletedByTimeout(timeoutResult))),
          future.map(CompletedNormally(_))
        )
      )
    }

  }

  sealed trait LimitedByTimeoutResult[T] {
    def value: T
  }

  final case class CompletedNormally[T](value: T) extends LimitedByTimeoutResult[T]

  final case class CompletedByTimeout[T](value: T) extends LimitedByTimeoutResult[T]

}
