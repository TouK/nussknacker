package pl.touk.esp.engine.flink.test

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobExecutionResult, JobSubmissionResult}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import org.apache.flink.test.util.TestBaseUtils

class StoppableExecutionEnvironment(userFlinkClusterConfig: Configuration) extends StreamExecutionEnvironment with LazyLogging {

  protected var localFlinkMiniCluster: LocalFlinkMiniCluster = _

  def getJobManagerActorSystem() = {
    localFlinkMiniCluster.jobManagerActorSystems.get.head
  }

  def execute(jobName: String): JobExecutionResult = {
    // transform the streaming program into a JobGraph
    val streamGraph: StreamGraph = getStreamGraph
    streamGraph.setJobName(jobName)
    val jobGraph: JobGraph = streamGraph.getJobGraph
    logger.info("Running job on local embedded Flink mini cluster")
    jobGraph.getJobConfiguration.addAll(userFlinkClusterConfig)
    localFlinkMiniCluster = TestBaseUtils.startCluster(jobGraph.getJobConfiguration, true)

    val submissionRes: JobSubmissionResult = localFlinkMiniCluster.submitJobDetached(jobGraph)
    new JobExecutionResult(submissionRes.getJobID, 0, new java.util.HashMap[String, AnyRef]())
  }

  def stop(): Unit = {
    if (localFlinkMiniCluster != null) {
      localFlinkMiniCluster.stop()
    }
  }

}
