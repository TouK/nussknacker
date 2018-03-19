package pl.touk.nussknacker.engine.sql

import java.util
import SqlType._
import cats.implicits._

import org.scalatest.{FunSuite, Matchers}

class FillTablesTest extends FunSuite with Matchers {
  test("fulfillment") {
    def createMap[T](dogos: T, chairs: T): Map[String, T] = Map(
      "dogos" -> dogos,
      "chairs" -> chairs
    )

    val vars = createMap(
      List(Dog("azor", 2), Dog("reksio", 5)),
      List(Chair("red"), Chair("blue"), Chair("green"))
    )

    val result = FillTables(vars, createMap(dogosModel, chairsModel), ReadObjectField)

    val dogosTable = Table(dogosModel, List(List("azor", 2), List("reksio", 5)))
    val chairsTable = Table(chairsModel, List(List("red"), List("blue"), List("green")))
    result shouldEqual createMap(dogosTable, chairsTable).valid

  }
  test("machalling wraps serves Java class"){
    val list = util.Arrays.asList(Chair("a"))
    FillTables.marshall("",ReadObjectField, chairsModel, list) shouldEqual List(List("a")).valid
  }

  test("skip unused variables") {
    FillTables(Map("unused" -> List(1, 2, 3)), Map(), ReadObjectField) shouldEqual Map().valid
  }

  val dogosModel = ColumnModel(List(Column("name", Varchar), Column("age", Numeric)))
  val chairsModel = ColumnModel(List(Column("color", Varchar)))


  test("marshalling simple list") {
    val chairs = List(Chair("red"), Chair("blue"), Chair("green"))
    val result = FillTables.marshall("chairs",ReadObjectField, chairsModel, chairs)
    result shouldEqual List(List("red"), List("blue"), List("green")).valid
  }

  case class Dog(name: String, age: Int)

  case class Chair(color: String)

}
