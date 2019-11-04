package pl.touk.nussknacker.engine.flink.test


import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration._
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.runtime.jobgraph.{JobGraph, JobStatus}
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.flink.util.OptionalFailure

import scala.collection.JavaConverters._


object StoppableExecutionEnvironment {

  def withQueryableStateEnabled(configuration: Configuration, proxyPortLow: Int, proxyPortHigh: Int) : StoppableExecutionEnvironment= {
    //blaaa this is needed to make queryableState work with two task manager instances
    configuration.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 2)
    configuration.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$proxyPortLow-$proxyPortHigh")

    new StoppableExecutionEnvironment(configuration, false)
  }

}

class StoppableExecutionEnvironment(userFlinkClusterConfig: Configuration,
                                    singleActorSystem: Boolean = true) extends StreamExecutionEnvironment with LazyLogging {

  private val config: MiniClusterResourceConfiguration
    = new MiniClusterResourceConfiguration.Builder()
    .setNumberSlotsPerTaskManager(2)
    .setNumberTaskManagers(1)
    .setConfiguration(userFlinkClusterConfig)
    .build
  private val mini = new MiniClusterWithClientResource(config)

  {
    mini.before()
  }

  def queryableClient(proxyPort: Int) : QueryableStateClient= {
    new QueryableStateClient("localhost", proxyPort)
  }

  def runningJobs(): Iterable[JobID] = {
    mini.getMiniCluster.listJobs().get().asScala.filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)
  }

  def withJobRunning[T](jobName: String)(action: => T): T = {
    execute(jobName)
    try {
      action
    } finally {
      cancel(jobName)
    }
  }

  def execute(jobName: String): JobExecutionResult = {
    // transform the streaming program into a JobGraph
    val streamGraph: StreamGraph = getStreamGraph
    streamGraph.setJobName(jobName)
    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.info("Running job on local embedded Flink mini cluster")

    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    getConfig.disableSysoutLogging()
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    val submissionRes = mini.getMiniCluster.submitJob(jobGraph).get()

    new JobExecutionResult(submissionRes.getJobID, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def cancel(name: String): Unit = {
    mini.getMiniCluster.listJobs().get().asScala.filter(_.getJobName == name).map(_.getJobId).foreach(mini.getClusterClient.cancel)
  }

  def stop(): Unit = {
    mini.after()
  }

}
