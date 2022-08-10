package pl.touk.nussknacker.engine.lite.kafka

import io.circe.syntax._
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.commons.io.FileUtils
import org.scalatest.TestSuite
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, LogicalTypesGenericRecordBuilder}
import pl.touk.nussknacker.engine.spel.Implicits._

import java.io.File
import java.nio.charset.StandardCharsets

trait NuKafkaRuntimeTestMixin { self: TestSuite =>

  protected def kafkaBoostrapServer: String

  protected def prepareTestCaseFixture(testCaseName: String, prepareScenario: (String, String) => CanonicalProcess): NuKafkaRuntimeTestTestCaseFixture = {
    val rootName = self.suiteName + "-" + testCaseName
    val inputTopic = rootName + "-input"
    val outputTopic = rootName + "-output"
    val errorTopic = rootName + "-error"
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(outputTopic, 1)
    kafkaClient.createTopic(errorTopic, 1)
    val scenarioFile = saveScenarioToTmp(prepareScenario(inputTopic, outputTopic), rootName)
    NuKafkaRuntimeTestTestCaseFixture(inputTopic, outputTopic, errorTopic, scenarioFile)
  }

  private def saveScenarioToTmp(scenario: CanonicalProcess, scenarioFilePrefix: String): File = {
    val jsonFile = File.createTempFile(scenarioFilePrefix, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, scenario.asJson.spaces2, StandardCharsets.UTF_8)
    jsonFile
  }

  protected def deploymentDataFile: File = new File(getClass.getResource("/sampleDeploymentData.conf").getFile)

  protected def kafkaClient: KafkaClient

}

case class NuKafkaRuntimeTestTestCaseFixture(inputTopic: String, outputTopic: String, errorTopic: String, scenarioFile: File)

object NuKafkaRuntimeTestSamples {

  def jsonPingPongScenario(inputTopic: String, outputTopic: String): CanonicalProcess = ScenarioBuilder
    .streamingLite("json-ping-pong")
    .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
    .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")
    .toCanonicalProcess

  def avroPingPongScenario(inputTopic: String, outputTopic: String): CanonicalProcess = ScenarioBuilder
    .streamingLite("avro-ping-pong")
    .source("source", "kafka-avro", "Topic" -> s"'$inputTopic'", "Schema version" -> "'latest'")
    .emptySink("sink",
      "kafka-avro-raw",
      TopicParamName -> s"'$outputTopic'",
      SchemaVersionParamName -> "'latest'",
      SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'",
      SinkKeyParamName -> "",
      SinkValueParamName -> "#input"
    ).toCanonicalProcess

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
