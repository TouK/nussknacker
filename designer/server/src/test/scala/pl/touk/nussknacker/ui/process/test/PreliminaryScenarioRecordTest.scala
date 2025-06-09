package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class PreliminaryScenarioRecordTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("should encode and decode source-specific format record") {
    val inputRecord: PreliminaryScenarioRecord = SourceSpecificFormatPreliminaryScenarioRecord(
      sourceId = "source 1",
      record = Json.obj("f1" -> Json.fromLong(42), "f2" -> Json.fromString("str")),
      timestamp = Some(159L)
    )
    val recordJsonString = """{"sourceId":"source 1","record":{"f1":42,"f2":"str"},"timestamp":159}"""

    inputRecord.asJson.noSpaces shouldBe recordJsonString
    decode[PreliminaryScenarioRecord](recordJsonString).rightValue shouldBe inputRecord
  }

  test("should encode and decode common format record") {
    val inputRecord: PreliminaryScenarioRecord = CommonFormatPreliminaryScenarioRecord(
      sourceId = "source 1",
      variables = Map("input" -> Json.obj("f1" -> Json.fromLong(42), "f2" -> Json.fromString("str"))),
      timestamp = Some(159L)
    )
    val recordJsonString = """{"sourceId":"source 1","variables":{"input":{"f1":42,"f2":"str"}},"timestamp":159}"""

    inputRecord.asJson.noSpaces shouldBe recordJsonString
    decode[PreliminaryScenarioRecord](recordJsonString).rightValue shouldBe inputRecord
  }

}
