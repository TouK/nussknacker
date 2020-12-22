package pl.touk.nussknacker.engine.process

import java.lang
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.flink.test.{FlinkMiniClusterHolder, FlinkSpec, FlinkTestConfiguration}
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.test.{AvailablePortFinder, ExtremelyPatientScalaFutures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QueryableStateTest extends FlatSpec with FlinkSpec with Matchers with KafkaSpec with LazyLogging with ExtremelyPatientScalaFutures {

  private var queryStateProxyPortLow: Int = _

  private implicit val booleanTypeInfo: TypeInformation[lang.Boolean] = TypeInformation.of(classOf[java.lang.Boolean])

  private val creator = new DevProcessConfigCreator

  private val taskManagersCount = 2
  private val configuration: Configuration = FlinkTestConfiguration.configuration(taskManagersCount = taskManagersCount)
  private var registrar: FlinkProcessRegistrar = _



  override protected def beforeAll(): Unit = {
    super[KafkaSpec].beforeAll()
    AvailablePortFinder.withAvailablePortsBlocked(1) {
      case head :: Nil =>
        queryStateProxyPortLow = head
        flinkMiniCluster = FlinkMiniClusterHolder(FlinkMiniClusterHolder.addQueryableStateConfiguration(configuration, queryStateProxyPortLow, taskManagersCount), prepareEnvConfig())
        flinkMiniCluster.start()
      case other => throw new AssertionError(s"Failed to generate port, received: $other")
    }
    val testConfig = TestConfig(kafkaZookeeperServer)
    val modelData = LocalModelData(testConfig, creator)
    registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(modelData, RunMode.Engine), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }


  it should "fetch queryable state for all keys" in {
    kafkaClient.createTopic("esp.signals")

    val lockProcess = EspProcessBuilder
      .id("queryableStateProc1")
      .parallelism(1)
      .exceptionHandler()
      .source("start", "oneSource")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .emptySink("sink", "sendSms")

    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), lockProcess, ProcessVersion.empty, DeploymentData.empty)
    val jobId = env.executeAndWaitForStart(lockProcess.id).getJobID
    try {
      //this port should not exist...
      val strangePort = 12345
      val client = FlinkQueryableClient(s"localhost:$strangePort, localhost:$queryStateProxyPortLow, localhost:${queryStateProxyPortLow + 1}")

      def queryState(jobId: String): Future[Boolean] = client.fetchState[java.lang.Boolean](
        jobId = jobId,
        queryName = "single-lock-state",
        key = DevProcessConfigCreator.oneElementValue).map(Boolean.box(_))

      //we have to be sure the job is *really* working
      eventually {
        flinkMiniCluster.runningJobs() should contain (jobId)
        queryState(jobId.toString).futureValue shouldBe true
      }
      creator.signals(ProcessObjectDependencies(TestConfig(kafkaZookeeperServer), ObjectNamingProvider(getClass.getClassLoader), RunMode.Engine)).values
        .head.value.sendSignal(DevProcessConfigCreator.oneElementValue)(lockProcess.id)

      eventually {
        queryState(jobId.toString).futureValue shouldBe false
      }
    } finally {
      env.stopJob(lockProcess.id, jobId)
    }
  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer): Config = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("signals.topic", fromAnyRef("esp.signals"))
      .withValue(DevProcessConfigCreator.emptyMockedSchemaRegistryProperty, fromAnyRef(true))
  }
}
