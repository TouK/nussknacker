package pl.touk.nussknacker.engine.flink.test


import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration._
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.runtime.execution.ExecutionState
import org.apache.flink.runtime.executiongraph.AccessExecutionJobVertex
import org.apache.flink.runtime.jobgraph.{JobGraph, JobStatus}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.flink.util.OptionalFailure
import org.scalactic.source.Position
import org.scalatest.Matchers
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.collection.JavaConverters._


object StoppableExecutionEnvironment {

  def withQueryableStateEnabled(configuration: Configuration, proxyPortLow: Int, proxyPortHigh: Int) : StoppableExecutionEnvironment= {
    //blaaa this is needed to make queryableState work with two task manager instances
    configuration.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 2)
    configuration.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$proxyPortLow-$proxyPortHigh")

    new StoppableExecutionEnvironment(configuration)
  }

}

class StoppableExecutionEnvironment(userFlinkClusterConfig: Configuration) extends StreamExecutionEnvironment
  with LazyLogging with Eventually with Matchers {

  private val config: MiniClusterResourceConfiguration
    = new MiniClusterResourceConfiguration.Builder()
    //TODO: what should be here?
    .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, 2))
    .setConfiguration(userFlinkClusterConfig)
    .build
  private val flinkMiniCluster = new MiniClusterWithClientResource(config)

  {
    flinkMiniCluster.before()
  }

  def queryableClient(proxyPort: Int) : QueryableStateClient= {
    new QueryableStateClient("localhost", proxyPort)
  }

  def runningJobs(): Iterable[JobID] = {
    flinkMiniCluster.getMiniCluster.listJobs().get().asScala.filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)
  }

  def withJobRunning[T](jobName: String)(action: => T): T = {
    val res = execute(jobName)
    waitForStart(res.getJobID, jobName)
    try {
      action
    } finally {
      cancel(jobName)
    }
  }

  private def patienceConfigForJobStart: PatienceConfig = PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(100, Millis)))

  private def waitForStart(jobID: JobID, name: String): Unit = {
    eventually {
      val executionVertices: Iterable[AccessExecutionJobVertex] = flinkMiniCluster.getMiniCluster.getExecutionGraph(jobID).get().getAllVertices.asScala.values
      val notRunning = executionVertices.filterNot(_.getAggregateState != ExecutionState.RUNNING)
      assert(notRunning.isEmpty, s"Some vertices of $name are still not running: ${notRunning.map(rs => s"${rs.getName} - ${rs.getAggregateState}")}")
    }(patienceConfigForJobStart, implicitly[Position])
  }

  def execute(jobName: String): JobExecutionResult = {
    // transform the streaming program into a JobGraph
    val streamGraph: StreamGraph = getStreamGraph
    streamGraph.setJobName(jobName)
    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.info("Running job on local embedded Flink flinkMiniCluster cluster")

    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    getConfig.disableSysoutLogging()
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    val submissionRes = flinkMiniCluster.getMiniCluster.submitJob(jobGraph).get()

    new JobExecutionResult(submissionRes.getJobID, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def cancel(name: String): Unit = {
    flinkMiniCluster.getMiniCluster.listJobs().get().asScala.filter(_.getJobName == name).map(_.getJobId).foreach(flinkMiniCluster.getClusterClient.cancel)
  }

  def stop(): Unit = {
    flinkMiniCluster.after()
  }

}
