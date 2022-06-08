package pl.touk.nussknacker.engine.avro.decode

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.encode.{BestEffortAvroEncoder, ValidationMode}

class BestEffortAvroDecoderTest extends FunSuite with Matchers {

  private val encoder = BestEffortAvroEncoder(ValidationMode.allowOptional)

  private val schema = AvroUtils.parseSchema(
    """{
      |  "type": "record",
      |  "namespace": "pl.touk.nussknacker.engine.avro",
      |  "name": "FullName",
      |  "fields": [
      |    { "name": "first", "type": "string" },
      |    { "name": "last", "type": "string" },
      |    { "name": "age", "type": "int" },
      |    { "name": "sex", "type": {"type":"enum","name":"Sex","symbols":["MALE","FEMALE"]}},
      |    { "name": "params", "type": {"type":"map","values":"long"}},
      |    { "name": "cities", "type": {"type":"array","items":"string"}},
      |    { "name": "company", "type": {"type":"record","name":"Company", "fields": [{"name": "name", "type": "string"}]}}
      |  ]
      |}
    """.stripMargin)

  test("testDecode") {
    val data = Map(
      "first" -> "Jan", "last" -> "Kowalski", "age" -> 18, "sex" -> "MALE",
      "params" -> Map("q1" -> 1, "q2" -> 2), "cities" -> List("Warsaw", "Lublin"),
      "company" -> Map("name" -> "Touk")
    )
    val record = encoder.encodeOrError(data, schema)
    val decoded = BestEffortAvroDecoder.decode(record)
    decoded shouldBe data
  }
}
