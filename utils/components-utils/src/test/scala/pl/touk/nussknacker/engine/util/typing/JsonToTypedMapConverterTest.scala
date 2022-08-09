package pl.touk.nussknacker.engine.util.typing

import io.circe.parser._
import org.scalatest.{ Inside}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class JsonToTypedMapConverterTest extends AnyFunSuite with Matchers with Inside {

  test("should handle decimal scale correctly") {
    val typedMap = JsonToTypedMapConverter.jsonToTypedMap(parse("""{"foo": 0.00}""").right.get)
    inside(typedMap.get("foo")) {
      case bd: java.math.BigDecimal =>
        bd.scale() shouldBe 2
        bd shouldEqual new java.math.BigDecimal("0.00")
    }
  }

}
