package pl.touk.nussknacker.sql.service

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.schema.{MetaDataProviderFactory, TableDefinition}
import pl.touk.nussknacker.sql.utils.BaseHsqlQueryEnricherTest
import org.scalatest.BeforeAndAfterEach

class DatabaseQueryEnricherHsqlTest
    extends BaseHsqlQueryEnricherTest
    with DatabaseQueryEnricherQueryWithEnricher
    with BeforeAndAfterEach {

  override val service =
    new DatabaseQueryEnricher(hsqlDbPoolConfig, new MetaDataProviderFactory().create(hsqlDbPoolConfig))

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE people (id INT, name VARCHAR(40));",
    "INSERT INTO people (id, name) VALUES (1, 'John')"
  )

  override protected def afterEach(): Unit = {
    val cleanupStatements = List(
      "TRUNCATE TABLE people;",
      "INSERT INTO people (id, name) VALUES (1, 'John')"
    )
    cleanupStatements.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  test("DatabaseQueryEnricher#implementation without cache") {
    val result = queryWithEnricher(
      "select * from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{ID: Integer, NAME: String}]"
    )
    result shouldBe List(
      TypedMap(Map("ID" -> 1, "NAME" -> "John"))
    )
  }

  test("DatabaseQueryEnricher#implementation without cache and with mixed lowercase and uppercase characters") {
    val result = queryWithEnricher(
      "select iD, NaMe from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{ID: Integer, NAME: String}]"
    )
    result shouldBe List(
      TypedMap(Map("NAME" -> "John", "ID" -> 1))
    )
  }

  test("DatabaseQueryEnricher#implementation update query") {
    val query = "UPDATE people SET name = 'Don' where id = ?"
    updateWithEnricher(query, conn, Map("arg1" -> 1.asInstanceOf[AnyRef]), service)

    val queryResultSet = conn.prepareStatement("SELECT * FROM people WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

}
