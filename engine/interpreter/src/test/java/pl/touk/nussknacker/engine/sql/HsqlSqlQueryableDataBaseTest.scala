package pl.touk.nussknacker.engine.sql
import SqlType._

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedMapTypingResult}

class HsqlSqlQueryableDataBaseTest extends FunSuite with Matchers {
  test("complete whole query") {
    val impl = new HsqlSqlQueryableDataBase()
    val dogoModel = ColumnModel(List(Column("name", Varchar)))
    impl.createTables(Map("dogos" -> dogoModel))
    impl.insertTables(Map("dogos" -> Table(dogoModel, List(List("Azor"), List("Reksio")))))
    val result = impl.query("select name from dogos")

    def dogoMap(name: String) = TypedMap(Map("NAME" -> name))

    result shouldEqual List(dogoMap("Azor"), dogoMap("Reksio"))

  }
  private val obj = HsqlSqlQueryableDataBase
  test("create table") {
    val result = obj.createTableQuery("tab1", ColumnModel(List(Column("col1", Numeric))))
    result shouldEqual "CREATE TABLE tab1 (col1 NUMERIC)"
  }
  test("create table with many columns") {
    val result = obj.createTableQuery("tab1", ColumnModel(List(Column("col1", Numeric), Column("col2", Varchar))))
    result shouldEqual "CREATE TABLE tab1 (col1 NUMERIC, col2 VARCHAR(50))"
  }
  test("create insert query") {
    val result = obj.insertTableQuery("tab1", ColumnModel(List(Column("col1", Varchar),Column("col2", Varchar))))
    result shouldEqual "INSERT INTO tab1 (col1, col2) VALUES (?, ?)"
  }
}
