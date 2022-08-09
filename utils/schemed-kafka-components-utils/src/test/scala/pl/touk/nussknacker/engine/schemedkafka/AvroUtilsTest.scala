package pl.touk.nussknacker.engine.schemedkafka

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java._
import java.time.LocalDateTime

class AvroUtilsTest extends AnyFunSuite with Matchers {

  test("testCreateRecord") {
    val recordUserSchema = AvroUtils.parseSchema("""{"name": "UserRecord","type":"record","fields":[{"name":"user","type":["null","string"]}]}""")
    val schema = AvroUtils.parseSchema(
      s"""
        |{
        |  "type": "record",
        |  "name": "Record",
        |  "fields": [
        |    {"name": "year", "type": ["null", "int"]},
        |    {"name":"recordUser","type":["null", $recordUserSchema]},
        |    {"name":"array","type":["null",{"type":"array","items":["null",{"type":"array","items":"string"}]}]},
        |    {"name":"arrayOfMap","type":["null",{"type":"array","items":["null",{"type":"map","values":"string"}]}]},
        |    {"name":"map","type":["null",{"type":"map","values":["null",{"type":"map","values":"string"}]}]},
        |    {"name":"mapOfArray","type":["null",{"type":"map","values":["null",{"type":"array","items":"string"}]}]}
        |  ]
        |}
        |
        |""".stripMargin)

    val company = "Touk"
    val name = "lcl"
    val year = LocalDateTime.now().getYear

    val data = Map(
      "year" -> year,
      "recordUser" -> Map("user" -> name),
      "array" -> List(List(company)),
      "arrayOfMap" -> List(Map("company" -> company)),
      "map" -> Map("key" -> Map("company" -> company)),
      "mapOfArray"-> Map("key" -> List(company))
    )

    val recordWithUser = new LogicalTypesGenericRecordBuilder(recordUserSchema).set("user", name).build()

    val record = new LogicalTypesGenericRecordBuilder(schema)
      .set("year", year)
      .set("recordUser", recordWithUser)
      .set("array", util.List.of[util.List[String]](util.List.of[String](company)))
      .set("arrayOfMap", util.List.of[util.Map[String, String]](util.Map.of("company", company)))
      .set("map", util.Map.of[String, util.Map[String, String]]("key", util.Map.of("company", company)))
      .set("mapOfArray", util.Map.of[String, util.List[String]]("key", util.List.of[String](company)))
      .build()

    AvroUtils.createRecord(schema, data) shouldBe record

  }

}
