package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import io.circe.generic.JsonCodec

case class TestData(testRecords: List[TestRecord])

// TODO multiple-sources-test: add optional timestamp
case class TestRecord(json: Json) {
  def asJsonString: String = {
    json.asString.getOrElse(throw new IllegalArgumentException(s"Expected JSON string but got: '$json'"))
  }
}

object TestData {
  // TODO multiple-sources-test: introduce ScenarioTestData
  def newLineSeparated(strs: String*): TestData = TestData(strs.toList.map(s => TestRecord(Json.fromString(s))))
}
