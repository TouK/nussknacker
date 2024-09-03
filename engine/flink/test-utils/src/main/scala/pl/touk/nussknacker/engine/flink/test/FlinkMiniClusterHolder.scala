package pl.touk.nussknacker.engine.flink.test

import java.util.concurrent.CompletableFuture

import org.apache.flink.api.common.{JobID, JobStatus}
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration._
import org.apache.flink.runtime.client.JobStatusMessage
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.jobgraph.JobGraph
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.concurrent.Eventually.{scaled, _}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.flink.test.FlinkMiniClusterHolder._

import scala.jdk.CollectionConverters._

/**
  * This interface provides compatibility for another Flink's version.
  * Instance of mini cluster holder should be created only once for many jobs.
  */
trait FlinkMiniClusterHolder {

  protected def userFlinkClusterConfig: Configuration

  protected def envConfig: AdditionalEnvironmentConfig

  def start(): Unit

  def stop(): Unit

  def cancelJob(jobID: JobID): Unit

  def submitJob(jobGraph: JobGraph): JobID

  def runningJobs(): Iterable[JobID]

  def listJobs(): Iterable[JobStatusMessage]

  def createExecutionEnvironment(): MiniClusterExecutionEnvironment = {
    new MiniClusterExecutionEnvironment(this, userFlinkClusterConfig, envConfig)
  }

  // We access miniCluster because ClusterClient doesn't expose getExecutionGraph and getJobStatus doesn't satisfy us
  // It returns RUNNING even when some vertices are not started yet
  def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph]

}

class FlinkMiniClusterHolderImpl(
    flinkMiniCluster: MiniClusterWithClientResource,
    protected val userFlinkClusterConfig: Configuration,
    protected val envConfig: AdditionalEnvironmentConfig
) extends FlinkMiniClusterHolder {

  override def start(): Unit = {
    flinkMiniCluster.before()
  }

  override def stop(): Unit = {
    flinkMiniCluster.after()
  }

  override def cancelJob(jobID: JobID): Unit =
    flinkMiniCluster.getClusterClient.cancel(jobID)

  override def submitJob(jobGraph: JobGraph): JobID =
    flinkMiniCluster.getClusterClient.submitJob(jobGraph).get()

  override def listJobs(): List[JobStatusMessage] =
    flinkMiniCluster.getClusterClient.listJobs().get().asScala.toList

  override def runningJobs(): List[JobID] =
    listJobs().filter(_.getJobState == JobStatus.RUNNING).map(_.getJobId)

  def getClusterClient: ClusterClient[_] = flinkMiniCluster.getClusterClient

  override def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph] =
    flinkMiniCluster.getMiniCluster.getExecutionGraph(jobId)

}

object FlinkMiniClusterHolder {

  def apply(
      userFlinkClusterConfig: Configuration,
      envConfig: AdditionalEnvironmentConfig = AdditionalEnvironmentConfig()
  ): FlinkMiniClusterHolder = {
    userFlinkClusterConfig.set[java.lang.Boolean](CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)
    new FlinkMiniClusterHolderImpl(resource, userFlinkClusterConfig, envConfig)
  }

  def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterWithClientResource = {
    val clusterConfig: MiniClusterResourceConfiguration = new MiniClusterResourceConfiguration.Builder()
      .setNumberTaskManagers(userFlinkClusterConfig.get(TaskManagerOptions.MINI_CLUSTER_NUM_TASK_MANAGERS))
      .setNumberSlotsPerTaskManager(
        userFlinkClusterConfig
          .get[Integer](TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue())
      )
      .setConfiguration(userFlinkClusterConfig)
      .build
    new MiniClusterWithClientResource(clusterConfig)
  }

  case class AdditionalEnvironmentConfig(
      detachedClient: Boolean = true,
      // On the CI, 10 seconds is sometimes too low
      defaultWaitForStatePatience: PatienceConfig =
        PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(10, Millis)))
  )

}
