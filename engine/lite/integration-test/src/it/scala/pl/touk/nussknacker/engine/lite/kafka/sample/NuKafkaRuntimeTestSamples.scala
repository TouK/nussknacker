package pl.touk.nussknacker.engine.lite.kafka.sample

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import pl.touk.nussknacker.engine.api.process.{ProcessName, TopicName}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  sinkKeyParamName,
  sinkRawEditorParamName,
  sinkValidationModeParamName,
  sinkValueParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, LogicalTypesGenericRecordBuilder}

object NuKafkaRuntimeTestSamples {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val pingPongScenarioName: ProcessName = ProcessName("universal-ping-pong")

  def pingPongScenario(inputTopic: TopicName.ForSource, outputTopic: TopicName.ForSink): CanonicalProcess =
    ScenarioBuilder
      .streamingLite(pingPongScenarioName.value)
      .source("source", "kafka", "Topic" -> s"'${inputTopic.name}'", "Schema version" -> "'latest'")
      .emptySink(
        "sink",
        "kafka",
        topicParamName.value              -> s"'${outputTopic.name}'",
        schemaVersionParamName.value      -> "'latest'",
        sinkRawEditorParamName.value      -> s"true",
        sinkValidationModeParamName.value -> s"'${ValidationMode.strict.name}'",
        sinkKeyParamName.value            -> "",
        sinkValueParamName.value          -> "#input"
      )

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

  val jsonPingSchema: JsonSchema = new JsonSchema("""{
      |   "type" : "object",
      |   "name" : "Ping",
      |   "properties" : {
      |      "foo" : { "type" : "string" }
      |   },
      |   "required": ["foo"]
      |}""".stripMargin)

  val avroPingSchema: Schema = AvroUtils.parseSchema(avroPingSchemaString)

  val avroPingRecord: GenericRecord =
    new LogicalTypesGenericRecordBuilder(avroPingSchema).set("foo", "ping").build()

}
