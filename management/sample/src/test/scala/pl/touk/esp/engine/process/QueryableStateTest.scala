package pl.touk.esp.engine.process

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.management.sample.TestProcessConfigCreator
import pl.touk.esp.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.esp.engine.spel

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.esp.engine.flink.queryablestate.EspQueryableClient
import pl.touk.esp.engine.flink.test.StoppableExecutionEnvironment
import pl.touk.esp.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import spel.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

class QueryableStateTest extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

  val creator = new TestProcessConfigCreator

  val stoppableEnv = StoppableExecutionEnvironment.withQueryableStateEnabled()
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
      .id("proc1")
      .parallelism(1)
      .exceptionHandler("param1" -> "errors")
      .source("start", "kafka-transaction")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .sink("sink", "sendSms")

    registrar.register(env, lockProcess)
    val jobId = env.execute().getJobID.toString

    eventually {
      implicit val booleanTypeInfo = TypeInformation.of(classOf[java.lang.Boolean])
      val fetchedStateFuture = new EspQueryableClient(stoppableEnv.queryableClient())
        .fetchState[java.lang.Boolean](jobId = jobId, queryName = "single-lock-state", key = "TestInput1")
      val booleanState = Await.result(fetchedStateFuture, Duration("10s"))
      booleanState shouldBe true
    }
  }
}

object TestConfig {
  def apply(kafkaZookeeperServer: KafkaZookeeperServer) = {
    ConfigFactory.empty()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.zkAddress", fromAnyRef(kafkaZookeeperServer.zkAddress))
      .withValue("signals.topic", fromAnyRef("esp.signals"))
      .withValue("checkpointInterval", fromAnyRef("10s"))
      .withValue("timeout", fromAnyRef("10s"))
  }
}