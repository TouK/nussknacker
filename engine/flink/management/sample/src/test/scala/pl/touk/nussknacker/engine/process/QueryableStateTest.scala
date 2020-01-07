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

  private val QueryStateProxyPortLow = AvailablePortFinder.findAvailablePorts(1).head

  private implicit val booleanTypeInfo: TypeInformation[lang.Boolean] = TypeInformation.of(classOf[java.lang.Boolean])

  private val creator = new DevProcessConfigCreator

  private val taskManagersCount = 2
  private val config: Configuration = FlinkTestConfiguration.configuration(taskManagersCount = taskManagersCount)
  private val stoppableEnv: StoppableExecutionEnvironment = StoppableExecutionEnvironment.withQueryableStateEnabled(config, QueryStateProxyPortLow, taskManagersCount)
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
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
      .exceptionHandler("param1" -> "'errors'")
      .source("start", "oneSource")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .emptySink("sink", "sendSms")

    registrar.register(env, lockProcess, ProcessVersion.empty)
    val jobId = stoppableEnv.execute().getJobID

    //this port should not exist...
    val strangePort = 12345
    val client = FlinkQueryableClient(s"localhost:$strangePort, localhost:$QueryStateProxyPortLow")

    def queryState(jobId: String): Future[Boolean] = client.fetchState[java.lang.Boolean](
      jobId = jobId,
      queryName = "single-lock-state",
      key = DevProcessConfigCreator.oneElementValue).map(Boolean.box(_))

    //we have to be sure the job is *really* working
    eventually {
      queryState(jobId.toString).futureValue shouldBe true
    }
    creator.signals(TestConfig(kafkaZookeeperServer)).values
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