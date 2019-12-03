package pl.touk.nussknacker.engine.flink.test


import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration._
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.jobgraph.{JobGraph, JobStatus}
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.{MiniClusterResource, MiniClusterResourceConfiguration}
import org.apache.flink.util.OptionalFailure
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.collection.JavaConverters._


object StoppableExecutionEnvironment {

  def apply(userFlinkClusterConfig: Configuration) = new StoppableExecutionEnvironment(userFlinkClusterConfig) with MiniClusterResourceFlink_1_7

  def addQueryableStateConfiguration(configuration: Configuration, proxyPortLow: Int, taskManagersCount: Int): Configuration = {
    val proxyPortHigh = proxyPortLow + taskManagersCount - 1
    configuration.setBoolean(QueryableStateOptions.ENABLE_QUERYABLE_STATE_PROXY_SERVER, true)
    configuration.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$proxyPortLow-$proxyPortHigh")
    configuration
  }

  def withQueryableStateEnabled(configuration: Configuration, proxyPortLow: Int, taskManagersCount: Int) : StoppableExecutionEnvironment= {
    StoppableExecutionEnvironment(addQueryableStateConfiguration(configuration, proxyPortLow, taskManagersCount))
  }

}

abstract class StoppableExecutionEnvironment(userFlinkClusterConfig: Configuration) extends StreamExecutionEnvironment
  with LazyLogging with PatientScalaFutures with Matchers {

  // For backward compatibility with Flink 1.6 we have here MiniClusterResource intstead of MiniClusterWithClientResource
  // TODO after breaking compatibility with 1.6, replace MiniClusterResource with MiniClusterWithClientResource
  protected def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterResource

  private lazy val flinkMiniCluster: MiniClusterResource = {
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)
    resource.before()
    resource.getClusterClient.setDetached(true)
    resource
  }

  def queryableClient(proxyPort: Int) : QueryableStateClient= {
    new QueryableStateClient("localhost", proxyPort)
  }

  def runningJobs(): Iterable[JobID] = {
    flinkMiniCluster.getClusterClient.listJobs().get().asScala.filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)
  }

  // Warning: this method assume that will be one job for all checks inside action. We highly recommend to execute
  // job once per test class and then do many concurrent scenarios basing on own unique keys in input.
  // Running multiple parallel instances of job in one test class can cause stealing of data from sources between those instances.
  def withJobRunning[T](jobName: String)(action: => T): T = {
    val executionResult = executeAndWaitForStart(jobName)
    try {
      action
    } finally {
      cancel(executionResult.getJobID)
    }
  }

  val defaultWaitForStatePatience: PatienceConfig = PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(100, Millis)))

  def executeAndWaitForStart[T](jobName: String): JobExecutionResult = {
    val res = execute(jobName)
    waitForStart(res.getJobID, jobName)()
    res
  }

  def waitForStart(jobID: JobID, name: String)(patience: PatienceConfig = defaultWaitForStatePatience): Unit = {
    waitForJobState(jobID, name, expectedState = ExecutionState.RUNNING)(patience)
  }

  def waitForJobState(jobID: JobID, name: String, expectedState: ExecutionState)(patience: PatienceConfig = defaultWaitForStatePatience): Unit = {
    eventually {
      // We access miniCluster because ClusterClient doesn't expose getExecutionGraph and getJobStatus doesn't satisfy us
      // It returns RUNNING even when some vertices are not started yet
      val executionGraph = getMiniCluster(flinkMiniCluster).getExecutionGraph(jobID).get()
      val executionVertices = executionGraph.getAllExecutionVertices.asScala
      val notRunning = executionVertices.filterNot(_.getExecutionState == expectedState)
      assert(notRunning.isEmpty, s"Some vertices of $name are still not running: ${notRunning.map(rs => s"${rs.getTaskNameWithSubtaskIndex} - ${rs.getExecutionState}")}")
    }(patience, implicitly[Position])
  }

  // see comment in waitForJobState
  protected def getMiniCluster(resource: MiniClusterResource): MiniCluster


  override def execute(streamGraph: StreamGraph): JobExecutionResult = {

    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.debug("Running job on local embedded Flink flinkMiniCluster cluster")

    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    getConfig.disableSysoutLogging()
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    // Is passed classloader is ok?
    val submissionResult = flinkMiniCluster.getClusterClient.submitJob(jobGraph, getClass.getClassLoader)

    new JobExecutionResult(submissionResult.getJobID, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def cancel(jobId: JobID): Unit = {
    flinkMiniCluster.getClusterClient.cancel(jobId)
  }

  def stop(): Unit = {
    flinkMiniCluster.after()
  }

}

trait MiniClusterResourceFlink_1_7 extends StoppableExecutionEnvironment {

  override def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterResource = {
    val clusterConfig: MiniClusterResourceConfiguration = new MiniClusterResourceConfiguration.Builder()
      .setNumberTaskManagers(userFlinkClusterConfig.getInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, ConfigConstants.DEFAULT_LOCAL_NUMBER_TASK_MANAGER))
      .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue()))
      .setConfiguration(userFlinkClusterConfig)
      .build
    new MiniClusterResource(clusterConfig)
  }

  override protected def getMiniCluster(resource: MiniClusterResource): MiniCluster =
    resource.asInstanceOf[org.apache.flink.runtime.testutils.MiniClusterResource].getMiniCluster

}