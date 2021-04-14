package pl.touk.nussknacker.engine.sql
import SqlType._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}

import scala.collection.immutable.ListMap

class HsqlSqlQueryableDataBaseTest extends FunSuite with Matchers {

  val dogoModel = ColumnModel(List(Column("name", Varchar)))
  val dogQuery = "select name from dogos"

  test("complete whole query") {

    val impl = new HsqlSqlQueryableDataBase(dogQuery, Map("dogos" -> dogoModel))

    val result = impl.query(Map("dogos" -> Table(dogoModel, List(List("Azor"), List("Reksio")))))

    def dogoMap(name: String) = TypedMap(ListMap("NAME" -> name))

    result shouldEqual List(dogoMap("Azor"), dogoMap("Reksio"))

  }

  test("work with empty tables") {
    val impl = new HsqlSqlQueryableDataBase(dogQuery, Map("dogos" -> dogoModel))

    val result = impl.query(Map("dogos" -> Table(dogoModel, List())))

    result shouldEqual List()
  }

  test("reuse connection correctly in next query") {

    val impl = new HsqlSqlQueryableDataBase(dogQuery, Map("dogos" -> dogoModel))

    val result1 = impl.query(Map("dogos" -> Table(dogoModel, List(List("Azor")))))
    val result2 = impl.query(Map("dogos" -> Table(dogoModel, List(List("Reksio")))))

    result1 should have length 1
    result1 should have length 1
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
