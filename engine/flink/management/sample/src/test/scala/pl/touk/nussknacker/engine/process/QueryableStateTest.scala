package pl.touk.nussknacker.engine.process

import java.lang

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.{AvailablePortFinder, KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QueryableStateTest extends FlatSpec with BeforeAndAfterAll with Matchers with KafkaSpec with LazyLogging with VeryPatientScalaFutures {

  private var queryStateProxyPortLow: Int = _

  private implicit val booleanTypeInfo: TypeInformation[lang.Boolean] = TypeInformation.of(classOf[java.lang.Boolean])

  private val creator = new DevProcessConfigCreator

  private val taskManagersCount = 2
  private val configuration: Configuration = FlinkTestConfiguration.configuration(taskManagersCount = taskManagersCount)
  private var stoppableEnv: StoppableExecutionEnvironment = _
  private var env: StreamExecutionEnvironment = _
  private var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    AvailablePortFinder.withAvailablePortsBlocked(1) {
      case head :: Nil =>
        queryStateProxyPortLow = head
        stoppableEnv = StoppableExecutionEnvironment.withQueryableStateEnabled(configuration, queryStateProxyPortLow, taskManagersCount)
        stoppableEnv.start()
      case other => throw new AssertionError(s"Failed to generate port, received: $other")

    }
    env = new StreamExecutionEnvironment(stoppableEnv)
    val testConfig = TestConfig(kafkaZookeeperServer)
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(testConfig, creator)), testConfig)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
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

    registrar.register(env, lockProcess, ProcessVersion.empty)
    val jobId = stoppableEnv.execute(lockProcess.id).getJobID
    stoppableEnv.waitForStart(jobId, lockProcess.id)()

    //this port should not exist...
    val strangePort = 12345
    val client = FlinkQueryableClient(s"localhost:$strangePort, localhost:$queryStateProxyPortLow, localhost:${queryStateProxyPortLow+1}")

    def queryState(jobId: String): Future[Boolean] = client.fetchState[java.lang.Boolean](
      jobId = jobId,
      queryName = "single-lock-state",
      key = DevProcessConfigCreator.oneElementValue).map(Boolean.box(_))

    //we have to be sure the job is *really* working
    eventually {
      stoppableEnv.runningJobs().toList should contain (jobId)
      queryState(jobId.toString).futureValue shouldBe true
    }
    creator.signals(ProcessObjectDependencies(TestConfig(kafkaZookeeperServer), DefaultObjectNaming)).values
      .head.value.sendSignal(DevProcessConfigCreator.oneElementValue)(lockProcess.id)

    eventually {
      queryState(jobId.toString).futureValue shouldBe false
    }

  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer): Config = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("signals.topic", fromAnyRef("esp.signals"))
  }
}
