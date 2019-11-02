package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import pl.touk.nussknacker.engine.management.sample.TestProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.spel.Implicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class QueryableStateTest extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec with LazyLogging with ScalaFutures {

  val QueryStateProxyPortLow = 9070
  val QueryStateProxyPortHigh = 9071

    override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

  implicit val booleanTypeInfo = TypeInformation.of(classOf[java.lang.Boolean])

  val creator = new TestProcessConfigCreator

  val config = FlinkTestConfiguration.configuration
  val stoppableEnv = StoppableExecutionEnvironment.withQueryableStateEnabled(config, QueryStateProxyPortLow, QueryStateProxyPortHigh)
  val env = new StreamExecutionEnvironment(stoppableEnv)
  var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = TestConfig(kafkaZookeeperServer)
    registrar = new StandardFlinkProcessCompiler(creator, config).createFlinkProcessRegistrar()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    stoppableEnv.stop()
  }

  it should "fetch queryable state for all keys" in {
    val lockProcess = EspProcessBuilder
      .id("queryableStateProc1")
      .parallelism(1)
      .exceptionHandler("param1" -> "'errors'")
      .source("start", "kafka-transaction")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .emptySink("sink", "sendSms")

    registrar.register(env, lockProcess, ProcessVersion.empty)
    val jobId = env.execute().getJobID.toString
    //this port should not exist...
    val strangePort = 12345
    val client = FlinkQueryableClient(s"localhost:$strangePort, localhost:$QueryStateProxyPortLow")


    def queryState(jobId: String): Future[Boolean] =
      client
        .fetchState[java.lang.Boolean](
          jobId = jobId,
          queryName = "single-lock-state",
          key = "TestInput1")
        .map {
          Boolean.box(_)
        }

    //we have to be sure the job is *really* working
    eventually {
      val state = queryState(jobId)
      Await.ready(state, 5 seconds).value.get shouldBe 'success
      state.futureValue shouldBe true
    }

  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer) = {
    ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("signals.topic", fromAnyRef("esp.signals"))
  }
}