package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalactic.source.Position
import org.scalatest.concurrent.Eventually
import org.scalatest.enablers.Retrying
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig
import pl.touk.nussknacker.engine.flink.test.MiniClusterExecutionEnvironment.JobIsNotInitialized

import scala.jdk.CollectionConverters._

class MiniClusterExecutionEnvironment(
    miniCluster: MiniCluster,
    envConfig: AdditionalEnvironmentConfig,
    val env: StreamExecutionEnvironment
) extends AutoCloseable {

  def withJobRunning[T](jobID: JobID)(actionToInvokeWithJobRunning: => T): T = {
    waitForStart(jobID)()
    try {
      val res = actionToInvokeWithJobRunning
      assertJobNotFailing(jobID)
      res
    } finally {
      stopJob(jobID)()
    }
  }

  def waitForFinished(
      jobID: JobID
  )(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): Unit = {
    waitForJobStatusWithAdditionalCheck(jobID, assertJobNotFailing(jobID), JobStatus.FINISHED)(patience)
  }

  private def waitForStart(jobID: JobID)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithNotFailingCheck(jobID, ExecutionState.RUNNING, ExecutionState.FINISHED)(patience)
  }

  private def waitForJobStateWithNotFailingCheck(jobID: JobID, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, assertJobNotFailing(jobID), expectedState: _*)(patience)
  }

  def assertJobNotFailing(jobID: JobID): Unit = {
    val executionGraph = miniCluster.getExecutionGraph(jobID).get()
    if (Set(JobStatus.FAILING, JobStatus.FAILED, JobStatus.RESTARTING).contains(executionGraph.getState)) {
      throw new Exception(
        s"Job: $jobID has failing state. Failure info: ${Option(executionGraph.getFailureInfo).map(_.getExceptionAsString).orNull}"
      )
    }
  }

  private def stopJob(jobID: JobID)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    miniCluster.cancelJob(jobID)
    waitForJobState(jobID, ExecutionState.CANCELED, ExecutionState.FINISHED, ExecutionState.FAILED)(patience)
  }

  private def waitForJobState(jobID: JobID, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, {}, expectedState: _*)(patience)
  }

  private def waitForJobStatusWithAdditionalCheck(
      jobID: JobID,
      additionalChecks: => Unit,
      expectedJobStatus: JobStatus
  )(
      patience: Eventually.PatienceConfig
  ): Unit = {
    Eventually.eventually {
      val executionGraph = miniCluster.getExecutionGraph(jobID).get()
      additionalChecks
      if (executionGraph.getState != expectedJobStatus) {
        throw new Exception(s"Job ${executionGraph.getJobName} does not have expected status: $expectedJobStatus")
      }
    }(patience, implicitly[Retrying[Unit]], implicitly[Position])
  }

  private def waitForJobStateWithAdditionalCheck(
      jobID: JobID,
      additionalChecks: => Unit,
      expectedState: ExecutionState*
  )(
      patience: Eventually.PatienceConfig
  ): Unit = {
    Eventually.eventually {
      val executionGraph = miniCluster.getExecutionGraph(jobID).get()
      // we have to verify if job is initialized, because otherwise, not all vertices are available so vertices status check
      // would be misleading
      if (executionGraph.getState == JobStatus.INITIALIZING) {
        throw new JobIsNotInitialized(jobID, executionGraph.getJobName)
      }
      additionalChecks
      val executionVertices  = executionGraph.getAllExecutionVertices.asScala
      val notInExpectedState = executionVertices.filterNot(v => expectedState.contains(v.getExecutionState))
      if (notInExpectedState.nonEmpty) {
        val message = notInExpectedState
          .map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")
          .mkString(
            s"Some vertices of ${executionGraph.getJobName} are not in expected (${expectedState.mkString(", ")}) state): ",
            ", ",
            ""
          )
        throw new Exception(message)
      }
    }(patience, implicitly[Retrying[Unit]], implicitly[Position])
  }

  override def close(): Unit = {
    env.close()
  }

}

object MiniClusterExecutionEnvironment {

  class JobIsNotInitialized(jobID: JobID, jobName: String)
      extends Exception(s"Job [id=$jobID, name=$jobName] is not initialized")

}
