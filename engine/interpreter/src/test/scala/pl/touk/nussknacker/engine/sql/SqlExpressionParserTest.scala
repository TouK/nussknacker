package pl.touk.nussknacker.engine.sql

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compiledgraph.expression.ExpressionParseError
import pl.touk.nussknacker.engine.sql.CreateColumnModel.{InvalidateMessage, NotAListMessage, UnknownInner}
import pl.touk.nussknacker.engine.sql.SqlType.Varchar

class SqlExpressionParserTest extends FunSuite with Matchers {
  test("valid column model should be passed") {
    val models = Map("tab11" -> ColumnModel(Nil).valid)
    val result = SqlExpressionParser.validateColumnModel(models)
    result shouldEqual Map("tab11" -> ColumnModel(Nil)).validNel
  }
  test("one invalid column model should casue Invalidated") {
    val models: Map[String, Validated[InvalidateMessage, ColumnModel]] =
      Map("tab11" -> ColumnModel(Nil).valid, "tab2" -> UnknownInner.invalid)
    val result = SqlExpressionParser.validateColumnModel(models)
    result shouldBe a[Invalid[_]]
  }
  test("NotAList message should be mapped to ExpressionParseError") {
    val resut = SqlExpressionParser.transform("t1", NotAListMessage(Typed[String]))
    resut.message should startWith("cannot create table from 't1' Typed")
    resut.message should  endWith("is not a list")
  }
  test("UnknownInner message should be mapped to ExpressionParseError") {
    val resut = SqlExpressionParser.transform("t1", UnknownInner)
    resut shouldEqual ExpressionParseError(s"cannot create table 't1'. List of Unknown")
  }
  test("valid query") {
    SqlExpressionParser.getQueryReturnType("select * from table1", columnModel) shouldBe a[Valid[_]]
  }
  test("query with unexciting table variable should invalidates") {
    SqlExpressionParser.getQueryReturnType("select * from unicorn", columnModel) shouldBe a[Invalid[_]]
  }
  test("query with unexciting select column should invalidates") {
    SqlExpressionParser.getQueryReturnType("select unicorn from table1", columnModel) shouldBe a[Invalid[_]]
  }
  val columnModel = Map("table1" -> ColumnModel(List(Column("col1", Varchar))))
}
