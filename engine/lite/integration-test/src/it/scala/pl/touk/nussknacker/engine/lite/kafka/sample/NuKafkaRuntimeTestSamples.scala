package pl.touk.nussknacker.engine.lite.kafka.sample

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  SchemaVersionParamName,
  SinkKeyParamName,
  SinkRawEditorParamName,
  SinkValidationModeParameterName,
  SinkValueParamName,
  TopicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, LogicalTypesGenericRecordBuilder}

object NuKafkaRuntimeTestSamples {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val pingPongScenarioName = ProcessName("universal-ping-pong")

  def pingPongScenario(inputTopic: String, outputTopic: String): CanonicalProcess = ScenarioBuilder
    .streamingLite(pingPongScenarioName.value)
    .source("source", "kafka", "Topic" -> s"'$inputTopic'", "Schema version" -> "'latest'")
    .emptySink(
      "sink",
      "kafka",
      TopicParamName                  -> s"'$outputTopic'",
      SchemaVersionParamName          -> "'latest'",
      SinkRawEditorParamName          -> s"true",
      SinkValidationModeParameterName -> s"'${ValidationMode.strict.name}'",
      SinkKeyParamName                -> "",
      SinkValueParamName              -> "#input"
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
