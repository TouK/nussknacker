package pl.touk.nussknacker.engine.avro.encode

import io.circe.Json
import io.circe.Json.{fromLong, fromString, obj}
import org.apache.avro.SchemaBuilder
import org.apache.avro.generic.GenericRecordBuilder
import org.scalatest.{FunSpec, Matchers}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

class AvroToJsonEncoderSpec extends FunSpec with Matchers {

  val avroToJsonEncoder: PartialFunction[Any, Json] = new AvroToJsonEncoder().encoder(BestEffortJsonEncoder.defaultForTests)

  it("should encode generic record") {
    val schema =
      SchemaBuilder.builder().record("test").fields()
        .requiredString("field1")
        .requiredLong("field2").endRecord()

    val genRec = new GenericRecordBuilder(schema).set("field1", "a").set("field2", 11).build()
    avroToJsonEncoder(genRec) shouldEqual obj("field1" -> fromString("a"), "field2" -> fromLong(11))
  }

}
