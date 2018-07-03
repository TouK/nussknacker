package pl.touk.nussknacker.engine.sql.columnmodel

import cats.data.Validated
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.ValidationContext
import pl.touk.nussknacker.engine.sql.SqlType.Varchar
import pl.touk.nussknacker.engine.sql._

class CreateTablesDefinitionTest extends FunSuite with Matchers {

  import CreateTablesDefinitionTest._

  val mockColumModelCreation: TypingResult => Validated.Valid[ColumnModel] = {
    case `dogos` => Validated.Valid(ColumnModel(List(Column("name", Varchar))))
    case _ => throw new IllegalStateException()
  }
  test("create ColumnsModel for ValidationContext") {
    val vc = ValidationContext(Map("dogs" -> dogos))
    val tables = vc.variables.mapValues(mockColumModelCreation)
    tables shouldEqual Map("dogs" -> Validated.Valid(ColumnModel(List(Column("name", Varchar)))))
  }

}

private object CreateTablesDefinitionTest {
  val dogos: TypingResult = TypedList[Dog]

  case class Dog(name: String)

}
