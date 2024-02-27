package pl.touk.nussknacker.engine.flink.table

import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SqlFromFileReaderTest extends AnyFunSuite with Matchers {

  test("read sql statements from resource") {
    val resourceName = "tables-definition-test1.sql"
    val statementFromFile =
      "CREATE TABLE Orders (\n    customerName STRING,\n    order_number INT\n) WITH (\n      'connector' = 'kafka'\n)"
    val statements = SqlFromResourceReader.readFileFromResources(resourceName)
    inside(statements) { case s :: Nil =>
      s shouldBe statementFromFile
    }
  }

}
