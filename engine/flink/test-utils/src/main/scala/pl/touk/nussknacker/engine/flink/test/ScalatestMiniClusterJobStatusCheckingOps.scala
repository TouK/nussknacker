package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.JobID
import org.apache.flink.runtime.minicluster.MiniCluster
import org.scalatest.concurrent.ScalaFutures.{PatienceConfig, convertScalaFuture, scaled}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.flink.minicluster.util.DurationToRetryPolicyConverter
import pl.touk.nussknacker.engine.flink.minicluster.{FlinkMiniClusterWithServices, MiniClusterJobStatusCheckingOps}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Future, blocking}
import scala.language.implicitConversions

object ScalatestMiniClusterJobStatusCheckingOps {

  private implicit val WaitForJobStatusPatience: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(50, Millis)))

  implicit def miniClusterWithServicesToOps(miniClusterWithServices: FlinkMiniClusterWithServices): Ops = new Ops(
    miniClusterWithServices.miniCluster
  )

  implicit class Ops(miniCluster: MiniCluster) {

    private val retryPolicy =
      DurationToRetryPolicyConverter.toPausePolicy(
        WaitForJobStatusPatience.timeout - 100.millis,
        WaitForJobStatusPatience.interval
      )

    def waitForJobIsFinished(jobID: JobID): Unit = {
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .waitForJobIsFinished(jobID)(retryPolicy, Some(retryPolicy))
        .futureValue
        .toTry
        .get
    }

    def withRunningJob[T](jobID: JobID)(actionToInvokeWithJobRunning: => T): T = {
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .withRunningJob(jobID)(retryPolicy, Some(retryPolicy)) {
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

    def checkJobIsNotFailing(jobID: JobID): Unit = {
      new MiniClusterJobStatusCheckingOps.Ops(miniCluster)
        .checkJobIsNotFailing(jobID)
        .futureValue
        .toTry
        .get
    }

  }

}
