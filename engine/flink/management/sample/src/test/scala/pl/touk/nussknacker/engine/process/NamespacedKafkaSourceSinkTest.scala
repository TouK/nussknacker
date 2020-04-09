package pl.touk.nussknacker.engine.process

import java.nio.charset.StandardCharsets

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.apache.flink.streaming.api.scala._
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.namespaces.{NamingContext, ObjectNaming, ObjectNamingUsageKey}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.management.sample.signal.RemoveLockProcessSignalFactory
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData



class NamespacedKafkaSourceSinkTest extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers {
  private implicit val stringTypeInfo: GenericTypeInfo[String] = new GenericTypeInfo(classOf[String])

  import KafkaUtils._
  import spel.Implicits._

  private val namespaceName: String = "ns"
  private val inputTopic: String = "input"
  private val outputTopic: String = "output"
  private def namespacedTopic(topicName: String): String = s"${namespaceName}_$topicName"

  test("should send message to topic with appended namespace") {
    val message = "dummy message"
    kafkaClient.sendMessage(namespacedTopic(inputTopic), message)

    val process = EspProcessBuilder
      .id("id")
      .parallelism(1)
      .exceptionHandler()
      .source("input", "real-kafka", "topic" -> s"'$inputTopic'")
      .sink("output", "#input", "kafka-string", "topic" -> s"'$outputTopic'")

    run(process) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer
        .consume(namespacedTopic(outputTopic))
        .take(1)
        .map(msg => new String(msg.message(), StandardCharsets.UTF_8))
        .toList
      processed shouldEqual List(message)
    }
  }

  private lazy val configCreator: DevProcessConfigCreator = new TestProcessConfig

  private val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("namespace", fromAnyRef(namespaceName))
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, configCreator,
      objectNaming = new TestObjectNaming)), config)
  }

  override protected def afterAll(): Unit = {
    stoppableEnv.stop()
    super.afterAll()
  }

  private def run(process: EspProcess)(action: => Unit):Unit= {
    registrar.register(env, process, ProcessVersion.empty)
    stoppableEnv.withJobRunning(process.id)(action)
  }
}

case class TestObjectNaming() extends ObjectNaming {
  override def prepareName(originalName: String, namingContext: NamingContext): String = namingContext.usageKey match {
    case ObjectNamingUsageKey.kafkaTopic => s"ns_$originalName"
    case _ => originalName
  }

}

class TestProcessConfig extends DevProcessConfigCreator {
  override def signals(config: Config): Map[String, WithCategories[RemoveLockProcessSignalFactory]] = Map.empty
}