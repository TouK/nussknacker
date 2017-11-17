package pl.touk.nussknacker.engine.flink.test

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobID, JobSubmissionResult}
import org.apache.flink.configuration.{ConfigConstants, Configuration, QueryableStateOptions}
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.runtime.query.QueryableStateClient
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.TestBaseUtils

object StoppableExecutionEnvironment {

  def withQueryableStateEnabled(userFlinkClusterConfiguration: Configuration = new Configuration()) : StoppableExecutionEnvironment= {

    //blaaa this is needed to make queryableState work
    //LocalFlinkMiniCluster#221 i #237
    userFlinkClusterConfiguration.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, 2)
    userFlinkClusterConfiguration.setBoolean(QueryableStateOptions.SERVER_ENABLE, true)

    new StoppableExecutionEnvironment(userFlinkClusterConfiguration, false)
  }

}

class StoppableExecutionEnvironment(userFlinkClusterConfig: Configuration,
                                    singleActorSystem: Boolean = true) extends StreamExecutionEnvironment with LazyLogging {

  protected var localFlinkMiniCluster: LocalFlinkMiniCluster = _

  def getJobManagerActorSystem() = {
    localFlinkMiniCluster.jobManagerActorSystems.get.head
  }

  def queryableClient() : QueryableStateClient= {
    new QueryableStateClient(userFlinkClusterConfig, localFlinkMiniCluster.highAvailabilityServices)
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
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)
    localFlinkMiniCluster = TestBaseUtils.startCluster(jobGraph.getJobConfiguration, singleActorSystem)

    val submissionRes: JobSubmissionResult = localFlinkMiniCluster.submitJobDetached(jobGraph)
    new JobExecutionResult(submissionRes.getJobID, 0, new java.util.HashMap[String, AnyRef]())
  }

  def stop(): Unit = {
    if (localFlinkMiniCluster != null) {
      localFlinkMiniCluster.stop()
    }
  }

}
