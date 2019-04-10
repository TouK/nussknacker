package pl.touk.nussknacker.engine.flink.test

import java.io.File
import java.nio.file.{Files, Path}

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID, JobSubmissionResult}
import org.apache.flink.configuration._
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.TestBaseUtils
import org.apache.flink.util.OptionalFailure
import org.junit.Assert

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

  protected var localFlinkMiniCluster: LocalFlinkMiniCluster = _

  def getJobManagerActorSystem() = {
    localFlinkMiniCluster.jobManagerActorSystems.get.head
  }

  def queryableClient(proxyPort: Int) : QueryableStateClient= {
    new QueryableStateClient("localhost", proxyPort)
  }

  def runningJobs(): Iterable[JobID] = {
    localFlinkMiniCluster.currentlyRunningJobs
  }

  def execute(jobName: String): JobExecutionResult = {
    // transform the streaming program into a JobGraph
    val streamGraph: StreamGraph = getStreamGraph
    streamGraph.setJobName(jobName)
    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.info("Running job on local embedded Flink mini cluster")

    userFlinkClusterConfig.setBoolean(ConfigConstants.LOCAL_START_WEBSERVER, false)
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)

    localFlinkMiniCluster = new LocalFlinkMiniCluster(jobGraph.getJobConfiguration, singleActorSystem)
    localFlinkMiniCluster.start()

    val submissionRes: JobSubmissionResult = localFlinkMiniCluster.submitJobDetached(jobGraph)
    new JobExecutionResult(submissionRes.getJobID, 0, new java.util.HashMap[String, OptionalFailure[AnyRef]]())
  }

  def stop(): Unit = {
    if (localFlinkMiniCluster != null) {
      localFlinkMiniCluster.stop()
    }
  }

}
