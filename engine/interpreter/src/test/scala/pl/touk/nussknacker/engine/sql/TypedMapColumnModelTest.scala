package pl.touk.nussknacker.engine.sql

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult}
import SqlType._

class TypedMapColumnModelTest extends FunSuite with Matchers {
  test("create column model") {
    val typingResult = TypedMapTypingResult(Map("number" -> Typed[Int], "string" -> Typed[String]))
    val excpected = ColumnModel(List(Column("number", Numeric),Column("string", Varchar)))
    TypedMapColumnModel.create(typingResult) shouldEqual excpected
  }

}
