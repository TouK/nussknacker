package pl.touk.nussknacker.engine.requestresponse.utils

import java.nio.charset.StandardCharsets
import java.util
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.typed.TypedMap

class QueryStringTestDataParserSpec extends FunSuite with Matchers {

  test("should parse query") {
    val parser = new QueryStringTestDataParser

    parser.parseTestData(TestData("no=12&id=123&id=155&mode=test\nno=15&id=555&mode=prod".getBytes(StandardCharsets.UTF_8), 2)) shouldBe List(
      TypedMap(Map("no" -> "12", "id" -> util.Arrays.asList("123", "155"), "mode" -> "test")),
      TypedMap(Map("no" -> "15", "id" -> "555", "mode" -> "prod"))
    )
  }

}
