package pl.touk.nussknacker.engine.schemedkafka.encode

import io.circe.Json
import io.circe.Json.{fromLong, fromString, obj}
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

class AvroToJsonEncoderCustomisationSpec extends AnyFunSpec with Matchers {

  val avroToJsonEncoder: PartialFunction[Any, Json] =
    new AvroToJsonEncoderCustomisation().encoder(ToJsonEncoder.defaultForTests.encode)

  it("should encode generic record") {
    val schema =
      SchemaBuilder
        .builder()
        .record("test")
        .fields()
        .requiredString("field1")
        .requiredLong("field2")
        .endRecord()

    val genRec = new GenericRecordBuilder(schema).set("field1", "a").set("field2", 11).build()
    avroToJsonEncoder(genRec) shouldEqual obj("field1" -> fromString("a"), "field2" -> fromLong(11))
  }

}
