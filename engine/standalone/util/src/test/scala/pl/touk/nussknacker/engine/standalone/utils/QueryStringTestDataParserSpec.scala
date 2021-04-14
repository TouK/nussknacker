package pl.touk.nussknacker.engine.standalone.utils

import java.nio.charset.StandardCharsets
import java.util
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap

import scala.collection.immutable.ListMap

class QueryStringTestDataParserSpec extends FunSuite with Matchers {

  test("should parse query") {
    val parser = new QueryStringTestDataParser

    parser.parseTestData("no=12&id=123&id=155&mode=test\nno=15&id=555&mode=prod".getBytes(StandardCharsets.UTF_8)) shouldBe List(
      TypedMap(ListMap("no" -> "12", "id" -> util.Arrays.asList("123", "155"), "mode" -> "test")),
      TypedMap(ListMap("no" -> "15", "id" -> "555", "mode" -> "prod"))
    )
  }

}
