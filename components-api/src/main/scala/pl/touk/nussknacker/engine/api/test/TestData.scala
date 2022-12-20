package pl.touk.nussknacker.engine.api.test

import io.circe.Json
import io.circe.generic.JsonCodec

// TODO multiple-sources-test: rename TestData to SourceTestData?
case class TestData(testRecords: List[TestRecord])

// TODO multiple-sources-test: add optional timestamp
case class TestRecord(json: Json)

