package pl.touk.nussknacker.engine.api.test

import io.circe.Json

case class TestData(testRecords: List[TestRecord])

case class TestRecord(json: Json, timestamp: Option[Long] = None)

