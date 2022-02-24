package pl.touk.nussknacker.engine.util.typing

import io.circe.parser._
import org.scalatest.{FunSuite, Inside, Matchers}

class JsonToTypedMapConverterTest extends FunSuite with Matchers with Inside {

  test("should handle decimal scale correctly") {
    val typedMap = JsonToTypedMapConverter.jsonToTypedMap(parse("""{"foo": 0.00}""").right.get)
    inside(typedMap.get("foo")) {
      case bd: java.math.BigDecimal =>
        bd.scale() shouldBe 2
        bd shouldEqual new java.math.BigDecimal("0.00")
    }
  }

}
