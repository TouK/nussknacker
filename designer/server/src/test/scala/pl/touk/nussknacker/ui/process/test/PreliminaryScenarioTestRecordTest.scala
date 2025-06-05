package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class PreliminaryScenarioTestRecordTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  test("should encode and decoded test record") {
    val inputRecord = PreliminaryScenarioTestRecord(
      sourceId = "source 1",
      record = Json.obj("f1" -> Json.fromLong(42), "f2" -> Json.fromString("str")),
      timestamp = Some(159L)
    )
    val recordJsonString = """{"sourceId":"source 1","record":{"f1":42,"f2":"str"},"timestamp":159}"""

    inputRecord.asJson.noSpaces shouldBe recordJsonString
    decode[PreliminaryScenarioTestRecord](recordJsonString).rightValue shouldBe inputRecord
  }

}
