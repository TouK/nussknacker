package pl.touk.nussknacker.engine.sql

import cats.data.{NonEmptyList, Validated}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.sql.CreateColumnModel.NotAListMessage

class CreateColumnModelTest extends FunSuite with Matchers {

  test("throw NotAList for Unknown") {
    CreateColumnModel(Unknown) shouldEqual invalid(Unknown)
  }
  test("throw NotAList for TypedMapTypingResult") {
    val result = TypedMapTypingResult(Map.empty[String, TypingResult])
    CreateColumnModel(result) shouldEqual invalid(result)
  }

  private def invalid(result: TypingResult) = {
    Validated.Invalid(NotAListMessage(result))
  }

  test("throw NotAList for Typed if not a list") {
    CreateColumnModel(Typed[Int]) shouldEqual invalid(Typed[Int])
  }

  test("extract list generic type") {
    CreateColumnModel.getListInnerType(TypedList[Data1]) shouldEqual Validated.Valid(Typed[Data1])
  }

  case class Data1(name: String, value: Int)

}
