package pl.touk.nussknacker.engine.sql

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.expression.TypedExpression
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}

class SqlExpressionParserTest extends FunSuite with Matchers {


  val validationCtx = ValidationContext(Map(
    "table1" -> TypedList(List("col1" -> Typed[String])),
    "table1a" -> TypedList(List("col1" -> Typed[Long])),
    "table2" -> TypedList(List("stringField" -> Typed[String], "longField" -> Typed[Long]))
  ))

  test("valid query") {
    SqlExpressionParser.parse("select * from table1", validationCtx, Unknown) shouldBe a[Valid[_]]
  }
  test("query with unexisting table variable should invalidates") {
    SqlExpressionParser.parse("select * from unicorn", validationCtx, Unknown) shouldBe a[Invalid[_]]
  }
  test("query with unexisting select column should invalidates") {
    SqlExpressionParser.parse("select unicorn from table1", validationCtx, Unknown) shouldBe a[Invalid[_]]
  }


  test("find smallest tables set") {
    parseOrFail("select stringField from table2").expression.asInstanceOf[SqlExpression].columnModels.keySet shouldEqual Set("table2")
    parseOrFail("select col1, stringField from table1 t1, table2 t2").expression.asInstanceOf[SqlExpression].columnModels.keySet shouldEqual Set("table1", "table2")
  }

  private def parseOrFail(expression: String, ctx: ValidationContext = validationCtx): TypedExpression
    = SqlExpressionParser.parse(expression, ctx, Unknown).leftMap(err => fail(s"Failed to parse: $err")).merge

}
