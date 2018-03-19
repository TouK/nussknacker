package pl.touk.nussknacker.engine.sql

import org.scalatest.{FunSuite, Matchers}

class ParseSqlFromsQueryTest extends FunSuite with Matchers {

  shouldFindFroms("select a from table1", "table1" :: Nil)
  shouldFindFroms("select a from table1, table2", "table1" :: "table2" :: Nil)
  shouldFindFroms("select a from (select b from table1)", "table1" :: Nil)
  shouldFindFroms("select a from table1 left join table2", "table1" :: "table2" :: Nil)

  private def shouldFindFroms(query: String, froms: List[String]): Unit =
    test(s"parse $query uses $froms") {
      verifyParsing(query, SqlFromsQuery(froms))
    }


  private def verifyParsing(query: String, sqlFromsQuery: SqlFromsQuery) {
    val result = SqlExpressionParser.parseSqlFromsQuery(query, List("table1","table2"))
    result shouldEqual sqlFromsQuery
  }

}
