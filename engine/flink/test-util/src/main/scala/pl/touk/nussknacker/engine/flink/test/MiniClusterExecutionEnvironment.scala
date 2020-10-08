package pl.touk.nussknacker.engine.flink.test

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration._
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.util.OptionalFailure
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.concurrent.Eventually
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder.AdditionalEnvironmentConfig

import scala.collection.JavaConverters._

class MiniClusterExecutionEnvironment(flinkMiniClusterHolder: FlinkMiniClusterHolder, userFlinkClusterConfig: Configuration, envConfig: AdditionalEnvironmentConfig) extends StreamExecutionEnvironment
  with LazyLogging with Matchers {

  /**
    * @deprecated
    * Use flinkMiniClusterHolder.runningJobs() instead of this. MiniClusterExecutionEnvironment should be used only for manage one job.
    */
  @Deprecated
  def runningJobs(): Iterable[JobID] =
    flinkMiniClusterHolder.runningJobs()

  // Warning: this method assume that will be one job for all checks inside action. We highly recommend to execute
  // job once per test class and then do many concurrent scenarios basing on own unique keys in input.
  // Running multiple parallel instances of job in one test class can cause stealing of data from sources between those instances.
  def withJobRunning[T](jobName: String)(actionToInvokeWithJobRunning: => T): T = {
    val executionResult = executeAndWaitForStart(jobName)
    try {
      actionToInvokeWithJobRunning
    } finally {
      stopJob(jobName, executionResult)
    }
  }

  def stopJob[T](jobName: String, executionResult: JobExecutionResult): Unit = {
    stopJob(jobName, executionResult.getJobID)
  }

  def stopJob[T](jobName: String, jobID: JobID): Unit = {
    flinkMiniClusterHolder.cancelJob(jobID)
    waitForJobState(jobID, jobName, ExecutionState.CANCELED, ExecutionState.FINISHED, ExecutionState.FAILED)()
    cleanupGraph()
  }

  def executeAndWaitForStart[T](jobName: String): JobExecutionResult = {
    val res = execute(jobName)
    waitForStart(res.getJobID, jobName)()
    res
  }

  def executeAndWaitForFinished[T](jobName: String)(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): JobExecutionResult = {
    val res = execute(jobName)
    waitForJobState(res.getJobID, jobName, ExecutionState.FINISHED)(patience)
    res
  }

  def waitForStart(jobID: JobID, name: String)(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): Unit = {
    waitForJobState(jobID, name, ExecutionState.RUNNING, ExecutionState.FINISHED)(patience)
  }

  def waitForJobState(jobID: JobID, name: String, expectedState: ExecutionState*)(patience: Eventually.PatienceConfig = envConfig.defaultWaitForStatePatience): Unit = {
    Eventually.eventually {
      val executionGraph = flinkMiniClusterHolder.getExecutionGraph(jobID).get()
      val executionVertices = executionGraph.getAllExecutionVertices.asScala
      val notRunning = executionVertices.filterNot(v => expectedState.contains(v.getExecutionState))
      assert(notRunning.isEmpty, s"Some vertices of $name are still not running: ${notRunning.map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")}")
    }(patience, implicitly[Position])
  }

  override def execute(streamGraph: StreamGraph): JobExecutionResult = {
    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.debug("Running job on local embedded Flink flinkMiniCluster cluster")

    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    // Is passed classloader is ok?
    val jobId = flinkMiniClusterHolder.submitJob(jobGraph)

    new JobExecutionResult(jobId, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def cancel(jobId: JobID): Unit =
    flinkMiniClusterHolder.cancelJob(jobId)

  //this *has* to be done between tests, otherwise next .execute() will execute also current operators
  def cleanupGraph(): Unit = {
    transformations.clear()
  }

}
