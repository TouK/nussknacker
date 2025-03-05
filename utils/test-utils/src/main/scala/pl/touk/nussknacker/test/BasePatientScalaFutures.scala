package pl.touk.nussknacker.test

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Future
import scala.concurrent.duration._

trait BasePatientScalaFutures extends ScalaFutures with Eventually {

  implicit class FutureOps[T](future: Future[T]) {

    // Sometimes we know the maximum time after which a particular Future will complete. In this case,
    // we want to wait a little longer, to make sure that in case of a Failure, the inner Failure won't
    // be suppressed by exception "A timeout occurred waiting for a future to complete"
    def futureValueEnsuringInnerException(innerOperationDuration: FiniteDuration): T = {
      try {
        future.futureValue(Timeout(innerOperationDuration.plus(patienceConfig.timeout)))
      } catch {
        case ex: TestFailedException if ex.getCause != null => throw ex.getCause
      }
    }

  }

}
