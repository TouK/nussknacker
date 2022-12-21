package pl.touk.nussknacker.engine.api.test

import io.circe.Json

// TODO multiple-sources-test: rename TestData to SourceTestData?
case class TestData(testRecords: List[TestRecord])

// TODO multiple-sources-test: add optional timestamp
case class TestRecord(json: Json, timestamp: Option[Long] = None)

