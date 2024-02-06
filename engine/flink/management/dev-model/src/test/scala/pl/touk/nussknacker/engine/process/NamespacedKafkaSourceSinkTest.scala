package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.namespaces.DefaultNamespacedObjectNaming
import pl.touk.nussknacker.test.KafkaConfigProperties

// TODO local: remove this after rewrite to universal kafka components?
class NamespacedKafkaSourceSinkTest extends AnyFunSuite with FlinkSpec with KafkaSpec with Matchers {

  import KafkaFactory._
  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import spel.Implicits._

  override lazy val config = ConfigFactory
    .load()
    .withValue(KafkaConfigProperties.bootstrapServersProperty(), fromAnyRef(kafkaServer.kafkaAddress))
    .withValue("namespace", fromAnyRef(namespaceName))

  private val namespaceName: String                      = "ns"
  private val inputTopic: String                         = "input"
  private val outputTopic: String                        = "output"
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
      val processed =
        kafkaClient.createConsumer().consumeWithJson[String](s"ns_$outputTopic").take(1).map(_.message()).toList
      processed shouldEqual List(message)
    }
  }

  private lazy val configCreator: DevProcessConfigCreator = new DevProcessConfigCreator

  private lazy val modelData =
    LocalModelData(config, List.empty, configCreator = configCreator, objectNaming = DefaultNamespacedObjectNaming)

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)
    env.withJobRunning(process.name.value)(action)
  }

}
