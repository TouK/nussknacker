package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.JobID
import org.apache.flink.runtime.minicluster.MiniCluster
import org.scalatest.concurrent.ScalaFutures.{PatienceConfig, convertScalaFuture, scaled}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.flink.minicluster.{FlinkMiniClusterWithServices, MiniClusterJobStatusCheckingOps}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, blocking}
import scala.language.implicitConversions

object ScalatestMiniClusterJobStatusCheckingOps {

  private implicit val WaitForJobStatusPatience: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(10, Millis)))

  implicit def miniClusterWithServicesToOps(miniClusterWithServices: FlinkMiniClusterWithServices): Ops = new Ops(
    miniClusterWithServices.miniCluster
  )

  implicit class Ops(miniCluster: MiniCluster) {

    def waitForFinished(jobID: JobID): Unit = {
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .waitForFinished(jobID)(toRetryPolicy(WaitForJobStatusPatience))
        .futureValue
        .toTry
        .get
    }

    def withJobRunning[T](jobID: JobID)(actionToInvokeWithJobRunning: => T): T = {
      val retryPolicy = toRetryPolicy(WaitForJobStatusPatience)
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .withJobRunning(jobID, retryPolicy, retryPolicy) {
          Future {
            blocking {
              actionToInvokeWithJobRunning
            }
          }
        }
        .futureValue
        .toTry
        .get
    }

    def checkJobNotFailing(jobID: JobID): Unit = {
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .checkJobNotFailing(jobID)
        .futureValue
        .toTry
        .get
    }

  }

  private def toRetryPolicy(patience: PatienceConfig) = {
    val maxAttempts = Math.max(Math.round(patience.timeout / patience.interval).toInt, 1)
    val delta       = Span(50, Millis)
    val interval    = (patience.timeout - delta) / maxAttempts
    retry.Pause(maxAttempts, interval)
  }

}
