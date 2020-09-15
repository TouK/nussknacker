package pl.touk.nussknacker.engine.flink.test

import java.util.concurrent.CompletableFuture

import org.apache.flink.api.common.JobID
import org.apache.flink.client.program.ClusterClient
import org.apache.flink.configuration.{ConfigConstants, ConfigOptions, Configuration, CoreOptions, QueryableStateOptions, TaskManagerOptions}
import org.apache.flink.queryablestate.client.QueryableStateClient
import org.apache.flink.runtime.executiongraph.AccessExecutionGraph
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.concurrent.Eventually.scaled
import org.scalatest.time.{Millis, Seconds, Span}
import FlinkMiniClusterHolder._
import org.scalatest.concurrent.Eventually._

// This class is splitted into trait and Impl because of Flink's API changes between 1.6 and 1.7 version
trait FlinkMiniClusterHolder {

  protected def userFlinkClusterConfig: Configuration

  protected def envConfig: AdditionalEnvironmentConfig

  def start(): Unit

  def stop(): Unit

  def createExecutionEnvironment(): MiniClusterExecutionEnvironment = {
    new MiniClusterExecutionEnvironment(this, userFlinkClusterConfig, envConfig)
  }

  def getClusterClient: ClusterClient[_]

  // We access miniCluster because ClusterClient doesn't expose getExecutionGraph and getJobStatus doesn't satisfy us
  // It returns RUNNING even when some vertices are not started yet
  def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph]

  final def queryableClient(proxyPort: Int) : QueryableStateClient= {
    new QueryableStateClient("localhost", proxyPort)
  }

}

class FlinkMiniClusterHolderImpl(flinkMiniCluster: MiniClusterWithClientResource,
                                 protected val userFlinkClusterConfig: Configuration,
                                 protected val envConfig: AdditionalEnvironmentConfig) extends FlinkMiniClusterHolder {

  override def start(): Unit = {
    flinkMiniCluster.before()
  }

  override def stop(): Unit = {
    flinkMiniCluster.after()
  }

  override def getClusterClient: ClusterClient[_] = flinkMiniCluster.getClusterClient

  override def getExecutionGraph(jobId: JobID): CompletableFuture[_ <: AccessExecutionGraph] =
    flinkMiniCluster.getMiniCluster.getExecutionGraph(jobId)

}

object FlinkMiniClusterHolder {

  def apply(userFlinkClusterConfig: Configuration, envConfig: AdditionalEnvironmentConfig = AdditionalEnvironmentConfig()): FlinkMiniClusterHolder = {
    userFlinkClusterConfig.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true)
    val resource = prepareMiniClusterResource(userFlinkClusterConfig)
    new FlinkMiniClusterHolderImpl(resource, userFlinkClusterConfig, envConfig)
  }

  def addQueryableStateConfiguration(configuration: Configuration, proxyPortLow: Int, taskManagersCount: Int): Configuration = {
    val proxyPortHigh = proxyPortLow + taskManagersCount - 1
    configuration.setBoolean(QueryableStateOptions.ENABLE_QUERYABLE_STATE_PROXY_SERVER, true)
    configuration.setString(QueryableStateOptions.PROXY_PORT_RANGE, s"$proxyPortLow-$proxyPortHigh")
    configuration
  }

  def prepareMiniClusterResource(userFlinkClusterConfig: Configuration): MiniClusterWithClientResource = {
    val taskManagerNumber = ConfigOptions.key(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER).intType().defaultValue(ConfigConstants.DEFAULT_LOCAL_NUMBER_JOB_MANAGER)
    val clusterConfig: MiniClusterResourceConfiguration = new MiniClusterResourceConfiguration.Builder()
      .setNumberTaskManagers(userFlinkClusterConfig.get(taskManagerNumber))
      .setNumberSlotsPerTaskManager(userFlinkClusterConfig.getInteger(TaskManagerOptions.NUM_TASK_SLOTS, TaskManagerOptions.NUM_TASK_SLOTS.defaultValue()))
      .setConfiguration(userFlinkClusterConfig)
      .build
    new MiniClusterWithClientResource(clusterConfig)
  }

  case class AdditionalEnvironmentConfig(detachedClient: Boolean = true,
                                         defaultWaitForStatePatience: PatienceConfig = PatienceConfig(timeout = scaled(Span(20, Seconds)), interval = scaled(Span(100, Millis))))

}
