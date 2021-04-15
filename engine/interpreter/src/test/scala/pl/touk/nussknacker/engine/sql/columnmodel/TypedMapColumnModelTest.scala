package pl.touk.nussknacker.engine.sql.columnmodel

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.sql.SqlType.{Numeric, Varchar}
import pl.touk.nussknacker.engine.sql.{Column, ColumnModel}

class TypedMapColumnModelTest extends FunSuite with Matchers {
  test("create column model") {
    val typingResult = TypedObjectTypingResult(List("number" -> Typed[Int], "string" -> Typed[String]))
    val excpected = ColumnModel(List(Column("number", Numeric),Column("string", Varchar)))
    TypedMapColumnModel.create(typingResult) shouldEqual excpected
  }

}
