package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.{JobExecutionResult, JobID, JobStatus}
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.enablers.Retrying
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

import scala.jdk.CollectionConverters._

class MiniClusterExecutionEnvironment(
    miniCluster: MiniCluster,
    envConfig: AdditionalEnvironmentConfig,
    val env: StreamExecutionEnvironment
) extends AutoCloseable
    with Matchers {

  // Warning: this method assume that will be one job for all checks inside action. We highly recommend to execute
  // job once per test class and then do many concurrent scenarios basing on own unique keys in input.
  // Running multiple parallel instances of job in one test class can cause stealing of data from sources between those instances.
  def withJobRunning[T](jobName: String)(actionToInvokeWithJobRunning: => T): T =
    withJobRunning(jobName, _ => actionToInvokeWithJobRunning)

  def withJobRunning[T](jobName: String, actionToInvokeWithJobRunning: JobExecutionResult => T): T = {
    val executionResult: JobExecutionResult = executeAndWaitForStart(jobName)
    try {
      val res   = actionToInvokeWithJobRunning(executionResult)
      val jobID = executionResult.getJobID
      assertJobNotFailing(jobID)
      res
    } finally {
      stopJob(executionResult.getJobID)
    }
  }

  def executeAndWaitForStart(jobName: String): JobExecutionResult = {
    val res = env.execute(jobName)
    waitForStart(res.getJobID)()
    res
  }

  def executeAndWaitForFinished(
      jobName: String
  )(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): JobExecutionResult = {
    val res = env.execute(jobName)
    waitForJobStatusWithAdditionalCheck(res.getJobID, assertJobNotFailing(res.getJobID), JobStatus.FINISHED)(
      patience
    )
    res
  }

  def waitForStart(jobID: JobID)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithNotFailingCheck(jobID, ExecutionState.RUNNING, ExecutionState.FINISHED)(patience)
  }

  def waitForJobStateWithNotFailingCheck(jobID: JobID, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, assertJobNotFailing(jobID), expectedState: _*)(patience)
  }

  def assertJobNotFailing(jobID: JobID): Unit = {
    val executionGraph = miniCluster.getExecutionGraph(jobID).get()
    assert(
      !Set(JobStatus.FAILING, JobStatus.FAILED, JobStatus.RESTARTING).contains(executionGraph.getState),
      s"Job: $jobID has failing state. Failure info: ${Option(executionGraph.getFailureInfo).map(_.getExceptionAsString).orNull}"
    )
  }

  def stopJob(jobID: JobID): Unit = {
    miniCluster.cancelJob(jobID)
    waitForJobState(jobID, ExecutionState.CANCELED, ExecutionState.FINISHED, ExecutionState.FAILED)()
  }

  private def waitForJobState(jobID: JobID, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, {}, expectedState: _*)(patience)
  }

  private def waitForJobStatusWithAdditionalCheck(
      jobID: JobID,
      additionalChecks: => Unit,
      expectedJobStatus: JobStatus
  )(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    Eventually.eventually {
      val executionGraph = miniCluster.getExecutionGraph(jobID).get()
      additionalChecks
      assert(
        executionGraph.getState.equals(expectedJobStatus),
        s"Job ${executionGraph.getJobName} does not have expected status: $expectedJobStatus"
      )
    }(patience, implicitly[Retrying[Assertion]], implicitly[Position])
  }

  private def waitForJobStateWithAdditionalCheck(
      jobID: JobID,
      additionalChecks: => Unit,
      expectedState: ExecutionState*
  )(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    Eventually.eventually {
      val executionGraph = miniCluster.getExecutionGraph(jobID).get()
      // we have to verify if job is initialized, because otherwise, not all vertices are available so vertices status check
      // would be misleading
      assert(executionGraph.getState != JobStatus.INITIALIZING)
      additionalChecks
      val executionVertices  = executionGraph.getAllExecutionVertices.asScala
      val notInExpectedState = executionVertices.filterNot(v => expectedState.contains(v.getExecutionState))
      assert(
        notInExpectedState.isEmpty,
        notInExpectedState
          .map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")
          .mkString(
            s"Some vertices of ${executionGraph.getJobName} are not in expected (${expectedState.mkString(", ")}) state): ",
            ", ",
            ""
          )
      )
    }(patience, implicitly[Retrying[Assertion]], implicitly[Position])
  }

  override def close(): Unit = {
    env.close()
  }

}
