package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import io.circe.generic.JsonCodec

// TODO multiple-sources-test: rename TestData to SourceTestData?
case class TestData(testRecords: List[TestRecord])

// TODO multiple-sources-test: add optional timestamp
case class TestRecord(json: Json) {
  def asJsonString: String = {
    json.asString.getOrElse(throw new IllegalArgumentException(s"Expected JSON string but got: '$json'"))
  }
}

object TestData {
  def asJsonStrings(strs: String*): TestData = TestData(strs.toList.map(s => TestRecord(Json.fromString(s))))
}
