package pl.touk.nussknacker.engine.requestresponse.utils

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.test.TestRecord
import pl.touk.nussknacker.engine.api.typed.TypedMap

import java.util

class QueryStringTestRecordParserSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  test("should parse query") {
    val testingData = Table(
      ("query", "result"),
      (
        "no=12&id=123&id=155&mode=test",
        TypedMap(Map("no" -> "12", "id" -> util.Arrays.asList("123", "155"), "mode" -> "test"))
      ),
      ("no=15&id=555&mode=prod", TypedMap(Map("no" -> "15", "id" -> "555", "mode" -> "prod")))
    )
    val parser = new QueryStringTestDataParser

    forAll(testingData) { (query: String, result: TypedMap) =>
      val testRecord = TestRecord(Json.fromString(query))
      parser.parse(List(testRecord)) shouldBe List(result)
    }
  }

}
