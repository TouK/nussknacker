package pl.touk.nussknacker.engine.process

import java.lang

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalactic.source.Position
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.{AvailablePortFinder, KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.management.sample.TestProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkStreamingProcessCompiler
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.test.{PatientScalaFutures, VeryPatientScalaFutures}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class QueryableStateTest extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec with LazyLogging with VeryPatientScalaFutures {

  private val QueryStateProxyPortLow = AvailablePortFinder.findAvailablePorts(1).head

  private implicit val booleanTypeInfo: TypeInformation[lang.Boolean] = TypeInformation.of(classOf[java.lang.Boolean])

  private val creator = new TestProcessConfigCreator

  private val config: Configuration = FlinkTestConfiguration.configuration
  private val stoppableEnv: StoppableExecutionEnvironment = StoppableExecutionEnvironment.withQueryableStateEnabled(config, QueryStateProxyPortLow, QueryStateProxyPortLow + 1)
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    registrar = new FlinkStreamingProcessCompiler(creator, config).createFlinkProcessRegistrar()
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }

  it should "fetch queryable state for all keys" in {
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
      key = TestProcessConfigCreator.oneElementValue).map(Boolean.box(_))

    //we have to be sure the job is *really* working
    eventually {
      queryState(jobId.toString).futureValue shouldBe true
    }
    creator.signals(TestConfig(kafkaZookeeperServer)).values
      .head.value.sendSignal(TestProcessConfigCreator.oneElementValue)(lockProcess.id)

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