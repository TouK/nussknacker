package pl.touk.nussknacker.engine.lite.kafka

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.commons.io.FileUtils
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.TestSuite
import pl.touk.nussknacker.engine.avro.{AvroUtils, LogicalTypesGenericRecordBuilder}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaUtils
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.Implicits._

import java.io.File
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters.RichOptionForJava8

trait NuKafkaRuntimeTestMixin { self: TestSuite =>

  protected def kafkaBoostrapServer: String

  protected def prepareTestCaseFixture(testCaseName: String, prepareScenario: (String, String) => EspProcess): NuKafkaRuntimeTestTestCaseFixture = {
    val rootName = self.suiteName + "-" + testCaseName
    val inputTopic = rootName + "-input"
    val outputTopic = rootName + "-output"
    val errorTopic = rootName + "-error"
    // TODO: replace with KafkaClient when it stop to use zkAddress for its admin client
    KafkaUtils.usingAdminClient(kafkaBoostrapServer) { client =>
      client.createTopics(List(
        new NewTopic(inputTopic, Option.empty[Integer].asJava, Option.empty[java.lang.Short].asJava),
        new NewTopic(outputTopic, Option(1: Integer).asJava, Option.empty[java.lang.Short].asJava),
        new NewTopic(errorTopic, Option(1: Integer).asJava, Option.empty[java.lang.Short].asJava)
      ).asJava)
    }
    val scenarioFile = saveScenarioToTmp(prepareScenario(inputTopic, outputTopic), rootName)
    NuKafkaRuntimeTestTestCaseFixture(inputTopic, outputTopic, errorTopic, scenarioFile)
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

case class NuKafkaRuntimeTestTestCaseFixture(inputTopic: String, outputTopic: String, errorTopic: String, scenarioFile: File)

object NuKafkaRuntimeTestSamples {

  def jsonPingPongScenario(inputTopic: String, outputTopic: String): EspProcess = EspProcessBuilder
    .id("json-ping-pong")
    .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
    .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")

  def avroPingPongScenario(inputTopic: String, outputTopic: String): EspProcess = EspProcessBuilder
    .id("avro-ping-pong")
    .source("source", "kafka-avro", "Topic" -> s"'$inputTopic'", "Schema version" -> "'latest'")
    .emptySink("sink", "kafka-avro-raw", "Topic" -> s"'$outputTopic'", "Schema version" -> "'latest'", "Value validation mode" -> "'strict'", "Key" -> "", "Value" -> "#input")

  val jsonPingMessage: String =
    """{"foo":"ping"}""".stripMargin

  private val avroPingSchemaString: String =
    """{
      |   "type" : "record",
      |   "name" : "Ping",
      |   "fields" : [
      |      { "name" : "foo" , "type" : "string" }
      |   ]
      |}""".stripMargin

  val avroPingSchema: Schema = AvroUtils.parseSchema(avroPingSchemaString)

  val avroPingRecord: GenericRecord =
    new LogicalTypesGenericRecordBuilder(avroPingSchema).set("foo", "ping").build()

}