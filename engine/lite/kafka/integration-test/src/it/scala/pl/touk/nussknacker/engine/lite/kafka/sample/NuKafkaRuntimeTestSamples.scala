package pl.touk.nussknacker.engine.lite.kafka.sample

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.encode.ValidationMode
import pl.touk.nussknacker.engine.schemedkafka.{AvroUtils, LogicalTypesGenericRecordBuilder}

object NuKafkaRuntimeTestSamples {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val jsonPingPongScenarioId = "json-ping-pong"
  val avroPingPongScenarioId = "avro-ping-pong"

  def jsonPingPongScenario(inputTopic: String, outputTopic: String): CanonicalProcess = ScenarioBuilder
    .streamingLite(jsonPingPongScenarioId)
    .source("source", "kafka-json", "topic" -> s"'$inputTopic'")
    .emptySink("sink", "kafka-json", "topic" -> s"'$outputTopic'", "value" -> "#input")
    .toCanonicalProcess

  def avroPingPongScenario(inputTopic: String, outputTopic: String): CanonicalProcess = ScenarioBuilder
    .streamingLite(avroPingPongScenarioId)
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
