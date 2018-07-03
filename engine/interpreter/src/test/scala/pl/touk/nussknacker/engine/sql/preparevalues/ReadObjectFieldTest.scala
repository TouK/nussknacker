package pl.touk.nussknacker.engine.sql.preparevalues

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.sql.preparevalues.ReadObjectField.ClassValueNotFound

class ReadObjectFieldTest extends FunSuite with Matchers {

  test("get field by name") {
    ReadObjectField.readField(Chair("blue"), "color") shouldEqual "blue"
  }
  test("get def by name") {
    case class Cup() {
      def color = "red"
    }
    ReadObjectField.readField(Cup(), "color") shouldEqual "red"
  }
  test("get field by name is case insensitive") {
    ReadObjectField.readField(Chair("red"), "cOlOr") shouldEqual "red"
  }
  test("throws exception if field value is not found") {
    assertThrows[ClassValueNotFound] {
      ReadObjectField.readField(Chair("red"), "nonexcisting")
    }
  }
  test("reads value from typed map") {
    ReadObjectField.readField(TypedMap(Map("age" -> 5)), "age") shouldEqual 5
  }
  test("reads value from typed map case insensitive") {
    ReadObjectField.readField(TypedMap(Map("age" -> 5)), "AgE") shouldEqual 5
  }
  test("reads unexciting value from typed map") {
    assertThrows[ClassValueNotFound] {
      ReadObjectField.readField(TypedMap(Map("age" -> 5)), "name")
    }
  }

  case class Chair(color: String)

}
