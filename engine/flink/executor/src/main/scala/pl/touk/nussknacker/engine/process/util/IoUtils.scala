package pl.touk.nussknacker.engine.process.util

import cats.effect.{unsafe, IO}

import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}
import scala.concurrent.blocking
import scala.concurrent.duration.FiniteDuration

object IoUtils {

  sealed trait Error

  object Error {
    case object Timeout     extends Error
    case object Interrupted extends Error
  }

  implicit class SyncIoOps[A](val action: IO[A]) extends AnyVal {

    def unsafeRunTimedAttempt(limit: FiniteDuration)(implicit runtime: unsafe.IORuntime): Either[Error, A] = {
      unsafeRunTimed(limit)
    }

    // method based on IO.unsafeRunTimed (cats-effect 3.5.7)
    // original method does not return the real cause of termination (it was just an Option[A])
    // we want to have the real cause of termination - timeout or interruption
    // keep in sync with cats-effect
    private def unsafeRunTimed(limit: FiniteDuration)(implicit runtime: unsafe.IORuntime): Either[Error, A] = {
      val queue = new ArrayBlockingQueue[Either[Throwable, A]](1)

      action.unsafeRunAsync { r =>
        queue.offer(r)
        ()
      }

      try {
        val result = blocking(queue.poll(limit.toNanos, TimeUnit.NANOSECONDS))
        if (result eq null) Left(Error.Timeout) else result.fold(throw _, Right(_))
      } catch {
        case _: InterruptedException =>
          Left(Error.Interrupted)
      }
    }

  }

}
