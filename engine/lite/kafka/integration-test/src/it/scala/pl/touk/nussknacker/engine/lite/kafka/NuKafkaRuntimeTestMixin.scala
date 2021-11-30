package pl.touk.nussknacker.engine.lite.kafka

import org.apache.commons.io.FileUtils
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.TestSuite
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.{KafkaClient, KafkaUtils}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._

import scala.collection.JavaConverters._
import java.io.File
import java.nio.charset.StandardCharsets
import scala.compat.java8.OptionConverters.RichOptionForJava8

trait NuKafkaRuntimeTestMixin { self: TestSuite =>

  protected def kafkaBoostrapServer: String

  protected def prepareTestCaseFixture(testCaseName: String, prepareScenario: (String, String) => EspProcess): NuKafkaRuntimeTestTestCaseFixture = {
    val rootName = self.suiteName + "-" + testCaseName
    val inputTopic = rootName + "-input"
    val outputTopic = rootName + "-output"
    // TODO: replace with KafkaClient when it stop to use zkAddress for its admin client
    KafkaUtils.usingAdminClient(kafkaBoostrapServer) { client =>
      client.createTopics(List(
        new NewTopic(inputTopic, Option.empty[Integer].asJava, Option.empty[java.lang.Short].asJava),
        new NewTopic(outputTopic, Option(1: Integer).asJava, Option.empty[java.lang.Short].asJava)
      ).asJava)
    }
    val scenarioFile = saveScenarioToTmp(prepareScenario(inputTopic, outputTopic), rootName)
    NuKafkaRuntimeTestTestCaseFixture(inputTopic, outputTopic, scenarioFile)
  }

  private def saveScenarioToTmp(scenario: EspProcess, scenarioFilePrefix: String): File = {
    val canonicalScenario = ProcessCanonizer.canonize(scenario)
    val json = ProcessMarshaller.toJson(canonicalScenario)
    val jsonFile = File.createTempFile(getClass.getSimpleName, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, json.toString(), StandardCharsets.UTF_8)
    jsonFile
  }

}

case class NuKafkaRuntimeTestTestCaseFixture(inputTopic: String, outputTopic: String, scenarioFile: File)

object NuKafkaRuntimeTestSamples {

  def jsonPingPongScenario(inputTopic: String, outputTopic: String): EspProcess = EspProcessBuilder
    .id("json-ping-pong")
    .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
    .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

  val jsonPingMessage: String =
    """{"foo":"ping"}""".stripMargin

}