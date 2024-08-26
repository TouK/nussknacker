package pl.touk.nussknacker.engine.schemedkafka.encode

import cats.data.Validated.valid
import cats.data.ValidatedNel
import io.circe.Json
import io.circe.Json.{fromLong, fromString, obj}
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.everit.json.schema.Schema
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder
import pl.touk.nussknacker.engine.json.encode.ToJsonSchemaBasedEncoder

class AvroToJsonSchemaBasedEncoderCustomisationTest extends AnyFunSuite with Matchers {

  test("should encode avro generic record") {
    type WithError[T] = ValidatedNel[String, T]
    val encoder = new ToJsonSchemaBasedEncoder(ValidationMode.strict)
    val avroToJsonEncoder: PartialFunction[(Any, Schema, Option[String]), WithError[Json]] =
      new AvroToJsonSchemaBasedEncoderCustomisation().encoder(encoder.encodeBasedOnSchema)

    val avroSchema =
      SchemaBuilder
        .builder()
        .record("test")
        .fields()
        .requiredString("field1")
        .requiredLong("field2")
        .endRecord()

    val jsonSchema: Schema = JsonSchemaBuilder.parseSchema("""{
        |  "type": "object",
        |  "properties": {
        |    "field1": {
        |      "type": "string"
        |    },
        |    "field2": {
        |      "type": "number"
        |    }
        |  }
        |}""".stripMargin)

    val genRec = new GenericRecordBuilder(avroSchema).set("field1", "a").set("field2", 11).build()

    avroToJsonEncoder(genRec, jsonSchema, None) shouldEqual valid(
      obj("field1" -> fromString("a"), "field2" -> fromLong(11))
    )
  }

}
