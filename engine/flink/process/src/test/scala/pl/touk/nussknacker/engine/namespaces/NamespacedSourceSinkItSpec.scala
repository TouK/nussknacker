package pl.touk.nussknacker.engine.namespaces

import java.nio.charset.StandardCharsets

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.apache.flink.streaming.api.scala._
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestParsingUtils
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSinkFactory, KafkaSourceFactory, KafkaSpec, KafkaUtils}
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.engine.spel

class NamespacedSourceSinkItSpec extends FunSuite with BeforeAndAfterAll with KafkaSpec with Matchers {
  private implicit val stringTypeInfo: GenericTypeInfo[String] = new GenericTypeInfo(classOf[String])

  import KafkaUtils._
  import spel.Implicits._

  private val namespaceName: String = "ns"
  private val inputTopic: String = "input"
  private val outputTopic: String = "output"
  private def namespacedTopic(topicName: String): String = s"${namespaceName}_$topicName"

  test("should send message to topic with appended namespace") {
    val message = "dummy message"
    val process = EspProcessBuilder
        .id("id")
        .parallelism(1)
        .exceptionHandler()
        .source("input", "kafka-in", "topic" -> s"'$inputTopic'")
        .sink("output", "#input", "kafka-out", "topic" -> s"'$outputTopic'")


    run(process) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer.consume(namespacedTopic(outputTopic)).take(1).map(msg => new String(msg.message(), StandardCharsets.UTF_8))
      processed shouldBe message
    }
  }

  private lazy val configCreator = new EmptyProcessConfigCreator {
    override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = {
      val kafkaConfig = config.as[KafkaConfig]("kafka")
      Map("kafka-in" ->
        WithCategories(
          new KafkaSourceFactory[String](
            kafkaConfig, new SimpleStringSchema, None, TestParsingUtils.newLineSplit),
          "Default"))
    }

    override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = {
      val kafkaConfig = config.as[KafkaConfig]("kafka")
      Map("kafka-out" ->
        WithCategories(
          new KafkaSinkFactory(kafkaConfig, new SimpleSerializationSchema[Any](_, _.toString)),
          "Default"))
    }
  }

  private val stoppableEnv = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())
  private val env = new StreamExecutionEnvironment(stoppableEnv)
  private var registrar: FlinkStreamingProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.load()
      .withValue("kafka.kafkaAddress", fromAnyRef(kafkaZookeeperServer.kafkaAddress))
      .withValue("kafka.namespace", fromAnyRef(namespaceName))
    registrar = FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, configCreator)), config)
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
