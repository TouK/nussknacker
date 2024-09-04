package pl.touk.nussknacker.engine.flink.test

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID, JobStatus}
import org.apache.flink.client.deployment.executors.PipelineExecutorUtils
import org.apache.flink.configuration._
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.util.OptionalFailure
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.enablers.Retrying
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

import scala.jdk.CollectionConverters._

class MiniClusterExecutionEnvironment(
    flinkMiniClusterHolder: FlinkMiniClusterHolder,
    userFlinkClusterConfig: Configuration,
    envConfig: AdditionalEnvironmentConfig
) extends StreamExecutionEnvironment(userFlinkClusterConfig)
    with LazyLogging
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
      stopJob(jobName, executionResult)
    }
  }

  def stopJob(jobName: String, executionResult: JobExecutionResult): Unit = {
    stopJob(jobName, executionResult.getJobID)
  }

  def stopJob(jobName: String, jobID: JobID): Unit = {
    flinkMiniClusterHolder.cancelJob(jobID)
    waitForJobState(jobID, jobName, ExecutionState.CANCELED, ExecutionState.FINISHED, ExecutionState.FAILED)()
    cleanupGraph()
  }

  def executeAndWaitForStart(jobName: String): JobExecutionResult = {
    val res = execute(jobName)
    waitForStart(res.getJobID, jobName)()
    res
  }

  def executeAndWaitForFinished(
      jobName: String
  )(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): JobExecutionResult = {
    val res = execute(jobName)
    waitForJobStatusWithAdditionalCheck(res.getJobID, jobName, assertJobNotFailing(res.getJobID), JobStatus.FINISHED)(
      patience
    )
    res
  }

  def waitForStart(jobID: JobID, name: String)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithNotFailingCheck(jobID, name, ExecutionState.RUNNING, ExecutionState.FINISHED)(patience)
  }

  def waitForJobStateWithNotFailingCheck(jobID: JobID, name: String, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, name, assertJobNotFailing(jobID), expectedState: _*)(patience)
  }

  def waitForJobState(jobID: JobID, name: String, expectedState: ExecutionState*)(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    waitForJobStateWithAdditionalCheck(jobID, name, {}, expectedState: _*)(patience)
  }

  def waitForJobStatusWithAdditionalCheck(
      jobID: JobID,
      name: String,
      additionalChecks: => Unit,
      expectedJobStatus: JobStatus
  )(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    Eventually.eventually {
      val executionGraph = flinkMiniClusterHolder.getExecutionGraph(jobID).get()
      additionalChecks
      executionGraph.getState.equals(expectedJobStatus)
      assert(
        executionGraph.getState.equals(expectedJobStatus),
        s"Job $name does not have expected status: $expectedJobStatus"
      )
    }(patience, implicitly[Retrying[Assertion]], implicitly[Position])
  }

  def waitForJobStateWithAdditionalCheck(
      jobID: JobID,
      name: String,
      additionalChecks: => Unit,
      expectedState: ExecutionState*
  )(
      patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience
  ): Unit = {
    Eventually.eventually {
      val executionGraph = flinkMiniClusterHolder.getExecutionGraph(jobID).get()
      // we have to verify if job is initialized, because otherwise, not all vertices are available so vertices status check
      // would be misleading
      assertJobInitialized(executionGraph)
      additionalChecks
      val executionVertices  = executionGraph.getAllExecutionVertices.asScala
      val notInExpectedState = executionVertices.filterNot(v => expectedState.contains(v.getExecutionState))
      assert(
        notInExpectedState.isEmpty,
        notInExpectedState
          .map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")
          .mkString(s"Some vertices of $name are not in expected (${expectedState.mkString(", ")}) state): ", ", ", "")
      )
    }(patience, implicitly[Retrying[Assertion]], implicitly[Position])
  }

  // Protected, to be overridden in Flink < 1.13 compatibility layer
  protected def assertJobInitialized(executionGraph: AccessExecutionGraph): Assertion = {
    assert(executionGraph.getState != JobStatus.INITIALIZING)
  }

  def assertJobNotFailing(jobID: JobID): Unit = {
    val executionGraph = flinkMiniClusterHolder.getExecutionGraph(jobID).get()
    assert(
      !Set(JobStatus.FAILING, JobStatus.FAILED, JobStatus.RESTARTING).contains(executionGraph.getState),
      s"Job: $jobID has failing state. Failure info: ${Option(executionGraph.getFailureInfo).map(_.getExceptionAsString).orNull}"
    )
  }

  override def execute(streamGraph: StreamGraph): JobExecutionResult = {
    val jobGraph = PipelineExecutorUtils.getJobGraph(streamGraph, userFlinkClusterConfig, getUserClassloader)
    if (jobGraph.getSavepointRestoreSettings == SavepointRestoreSettings.none) {
      // similar behaviour to MiniClusterExecutor.execute - PipelineExecutorUtils.getJobGraph overrides a few settings done directly on StreamGraph by settings from configuration
      jobGraph.setSavepointRestoreSettings(streamGraph.getSavepointRestoreSettings)
    }

    logger.debug("Running job on local embedded Flink flinkMiniCluster cluster")

    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    val jobId = flinkMiniClusterHolder.submitJob(jobGraph)

    new JobExecutionResult(jobId, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def cancel(jobId: JobID): Unit =
    flinkMiniClusterHolder.cancelJob(jobId)

  // this *has* to be done between tests, otherwise next .execute() will execute also current operators
  def cleanupGraph(): Unit = {
    transformations.clear()
  }

}
