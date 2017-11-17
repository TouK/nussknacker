package pl.touk.nussknacker.engine.process

import java.lang

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.management.sample.TestProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.spel

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.flink.queryablestate.EspQueryableClient
import pl.touk.nussknacker.engine.flink.test.StoppableExecutionEnvironment
import pl.touk.nussknacker.engine.kafka.{KafkaSpec, KafkaZookeeperServer}
import spel.Implicits._
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global

class QueryableStateTest extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually with KafkaSpec with LazyLogging with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

  implicit val booleanTypeInfo = TypeInformation.of(classOf[java.lang.Boolean])

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
      .id("queryableStateProc1")
      .parallelism(1)
      .exceptionHandler("param1" -> "errors")
      .source("start", "kafka-transaction")
      .customNode("lock", "lockOutput", "lockStreamTransformer", "input" -> "#input")
      .sink("sink", "sendSms")

    registrar.register(env, lockProcess)
    val jobId = env.execute().getJobID.toString
    val client = new EspQueryableClient(stoppableEnv.queryableClient())


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