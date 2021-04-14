package pl.touk.nussknacker.engine.sql.preparevalues

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.sql.preparevalues.ReadObjectField.ClassValueNotFound

import scala.collection.immutable.ListMap

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

  test("throws exception if field value is not found") {
    assertThrows[ClassValueNotFound] {
      ReadObjectField.readField(Chair("red"), "nonexcisting")
    }
  }
  test("reads value from typed map") {
    ReadObjectField.readField(TypedMap(ListMap("age" -> 5)), "age") shouldEqual 5
  }
  test("reads unexciting value from typed map") {
    assertThrows[ClassValueNotFound] {
      ReadObjectField.readField(TypedMap(ListMap("age" -> 5)), "name")
    }
  }

  test("handles getter-style properties from beans") {
    ReadObjectField.readField(Chair(), "isField1") shouldEqual "val1"
    ReadObjectField.readField(Chair(), "getField3") shouldEqual true

  }

  case class Chair(color: String = "red", isField1: String = "val1", getField3: Boolean = true)

}
