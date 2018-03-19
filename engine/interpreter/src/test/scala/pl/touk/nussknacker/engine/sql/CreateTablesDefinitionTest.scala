package pl.touk.nussknacker.engine.sql

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ValidationContext
import SqlType._
import cats.data.Validated

class CreateTablesDefinitionTest extends FunSuite with Matchers {

  import CreateTablesDefinitionTest._

  val mockColumModelCreation: TypingResult => Validated.Valid[ColumnModel] = {
    case `dogos` => Validated.Valid(ColumnModel(List(Column("name", Varchar))))
    case _ => throw new IllegalStateException()
  }
  test("create ColumnsModel for ValidationContext") {
    val vc = ValidationContext(Map("dogs" -> dogos))
    val froms = SqlFromsQuery(List("dogs"))
    val tables = SqlExpressionParser.createTablesDefinition(vc, froms, mockColumModelCreation)
    tables shouldEqual Map("dogs" -> Validated.Valid(ColumnModel(List(Column("name", Varchar)))))
  }
  test("filter unused variables") {
    val vc = ValidationContext(Map("dogs" -> dogos))
    val froms = SqlFromsQuery(Nil)
    val tables = SqlExpressionParser.createTablesDefinition(vc, froms, mockColumModelCreation)
    tables shouldEqual Map()
  }

}

private object CreateTablesDefinitionTest {
  val dogos: TypingResult = TypedList[Dog]

  case class Dog(name: String)

}
