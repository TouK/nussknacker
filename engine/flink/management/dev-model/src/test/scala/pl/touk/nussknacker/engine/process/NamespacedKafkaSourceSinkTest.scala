package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.management.sample.signal.RemoveLockProcessSignalFactory
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{process, spel}
import pl.touk.nussknacker.test.KafkaConfigProperties

import java.nio.charset.StandardCharsets

class NamespacedKafkaSourceSinkTest extends AnyFunSuite with FlinkSpec with KafkaSpec with Matchers {
  private implicit val stringTypeInfo: GenericTypeInfo[String] = new GenericTypeInfo(classOf[String])

  import KafkaTestUtils._
  import spel.Implicits._
  import KafkaFactory._

  override lazy val config = ConfigFactory.load()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef(kafkaServer.kafkaAddress))
    .withValue("namespace", fromAnyRef(namespaceName))

  private val namespaceName: String = "ns"
  private val inputTopic: String = "input"
  private val outputTopic: String = "output"
  private def namespacedTopic(topicName: String): String = s"${namespaceName}_$topicName"

  test("should send message to topic with appended namespace") {
    val message = "dummy message"
    kafkaClient.sendMessage(namespacedTopic(inputTopic), message)

    val process = ScenarioBuilder
      .streaming("id")
      .parallelism(1)
      .source("input", "real-kafka", TopicParamName -> s"'$inputTopic'")
      .emptySink("output", "kafka-string", TopicParamName -> s"'$outputTopic'", SinkValueParamName -> "#input")

    run(process) {
      val consumer = kafkaClient.createConsumer()
      val processed = consumer
        .consume(s"ns_${outputTopic}")
        .take(1)
        .map(msg => new String(msg.message(), StandardCharsets.UTF_8))
        .toList
      processed shouldEqual List(message)
    }
  }

  private lazy val configCreator: DevProcessConfigCreator = new TestProcessConfig

  private var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = LocalModelData(config, configCreator)
    registrar = process.registrar.FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }
}

class TestProcessConfig extends DevProcessConfigCreator {
  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[RemoveLockProcessSignalFactory]] =
    Map.empty
}
