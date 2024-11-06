package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import org.apache.flink.formats.avro.typeutils.NkSerializableParsedSchema
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemaV2
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.{KafkaUniversalComponentTransformer, RuntimeSchemaData}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ContentTypes, SchemaId, SchemaWithMetadata}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaSupportDispatcher
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.ByteBuffer
import java.time.Instant
import java.util

class KafkaJsonItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  private val givenMatchingAvroObjV2 = avroEncoder.encodeRecordOrError(
    Map("first" -> "Jan", "middle" -> "Tomek", "last" -> "Kowalski"),
    RecordSchemaV2
  )

  test("should read json message from kafka without provided schema") {
    val inputTopic  = "input-topic-without-schema"
    val outputTopic = "output-topic-without-schema"

    kafkaClient.createTopic(inputTopic, 1)
    kafkaClient.createTopic(outputTopic, 1)
    sendAsJson(givenMatchingAvroObjV2.toString, ForSource(inputTopic), Instant.now.toEpochMilli)

    val process =
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          "start",
          "kafka",
          KafkaUniversalComponentTransformer.topicParamName.value       -> Expression.spel(s"'$inputTopic'"),
          KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel
        )
        .emptySink(
          "end",
          "kafka",
          KafkaUniversalComponentTransformer.sinkKeyParamName.value       -> "".spel,
          KafkaUniversalComponentTransformer.sinkRawEditorParamName.value -> "true".spel,
          KafkaUniversalComponentTransformer.sinkValueParamName.value     -> "#input".spel,
          KafkaUniversalComponentTransformer.topicParamName.value         -> s"'${outputTopic}'".spel,
          KafkaUniversalComponentTransformer.contentTypeParamName.value   -> s"'${ContentTypes.JSON.toString}'".spel,
          KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
        )

    run(process) {
      val processed = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head

      val schema = SchemaWithMetadata(
        OpenAPIJsonSchema("""{"type": "object"}"""),
        SchemaId.fromString(ContentTypes.JSON.toString)
      )
      val runtimeSchema = new RuntimeSchemaData(new NkSerializableParsedSchema[ParsedSchema](schema.schema), None)
      val response =
        UniversalSchemaSupportDispatcher(kafkaConfig)
          .forSchemaType("JSON")
          .payloadDeserializer
          .deserialize(
            Some(runtimeSchema),
            runtimeSchema,
            ByteBuffer.wrap(processed.value())
          )
          .asInstanceOf[util.HashMap[String, String]]

      response.forEach((key, value) => givenMatchingAvroObjV2.get(key) shouldBe value)

    }
  }

}
