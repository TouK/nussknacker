package pl.touk.nussknacker.engine.flink.table

import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SqlFromFileReaderTest extends AnyFunSuite with Matchers {

  test("read sql statements from resource") {
    val resourceName = "tables-definition-test1.sql"
    val statement1FromFile =
      """|CREATE TABLE TestTable1
         |(
         |    someString  STRING,
         |    someVarChar VARCHAR(150),
         |    someInt     INT
         |) WITH (
         |      'connector' = 'datagen'
         |)""".stripMargin
    val statement2FromFile =
      s"""|CREATE TABLE TestTable2
          |(
          |    someString2  STRING,
          |    someVarChar2 VARCHAR(150),
          |    someInt2     INT
          |) WITH (
          |      'connector' = 'datagen'
          |)""".stripMargin
    val statements = SqlFromResourceReader.readFileFromResources(resourceName)
    inside(statements) { case s1 :: s2 :: Nil =>
      s1 shouldBe statement1FromFile
      s2 shouldBe statement2FromFile
    }
  }

}
