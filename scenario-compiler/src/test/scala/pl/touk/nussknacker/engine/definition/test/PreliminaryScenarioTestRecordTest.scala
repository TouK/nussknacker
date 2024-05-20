package pl.touk.nussknacker.engine.definition.test

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class PreliminaryScenarioTestRecordTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("should encode and decoded standard record") {
    val specificRecord: PreliminaryScenarioTestRecord.Standard = PreliminaryScenarioTestRecord.Standard(
      sourceId = "source 1",
      record = Json.obj("f1" -> Json.fromLong(42), "f2" -> Json.fromString("str")),
      timestamp = Some(159L)
    )
    val baseRecord: PreliminaryScenarioTestRecord = specificRecord
    val recordJsonString = """{"sourceId":"source 1","record":{"f1":42,"f2":"str"},"timestamp":159}"""

    specificRecord.asJson.noSpaces shouldBe recordJsonString
    baseRecord.asJson.noSpaces shouldBe recordJsonString
    decode[PreliminaryScenarioTestRecord.Standard](recordJsonString).rightValue shouldBe specificRecord
    decode[PreliminaryScenarioTestRecord](recordJsonString).rightValue shouldBe specificRecord
  }

  test("should encode and decode simplified record") {
    val specificRecord: PreliminaryScenarioTestRecord.Simplified = PreliminaryScenarioTestRecord.Simplified(
      record = Json.obj("f1" -> Json.fromLong(42), "f2" -> Json.fromString("str")),
    )
    val baseRecord: PreliminaryScenarioTestRecord = specificRecord
    val recordJsonString                          = """{"f1":42,"f2":"str"}"""

    specificRecord.asJson.noSpaces shouldBe recordJsonString
    baseRecord.asJson.noSpaces shouldBe recordJsonString
    decode[PreliminaryScenarioTestRecord.Simplified](recordJsonString).rightValue shouldBe specificRecord
    decode[PreliminaryScenarioTestRecord](recordJsonString).rightValue shouldBe specificRecord
  }

}
