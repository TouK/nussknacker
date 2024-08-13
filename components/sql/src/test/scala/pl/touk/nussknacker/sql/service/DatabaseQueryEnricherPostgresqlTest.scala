package pl.touk.nussknacker.sql.service

import org.scalatest.BeforeAndAfterEach
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.schema.MetaDataProviderFactory
import pl.touk.nussknacker.sql.utils.BasePostgresqlQueryEnricherTest

import java.time.{LocalDate, LocalTime, ZonedDateTime}

class DatabaseQueryEnricherPostgresqlTest
    extends BasePostgresqlQueryEnricherTest
    with DatabaseQueryEnricherQueryWithEnricher
    with BeforeAndAfterEach {

  override val service =
    new DatabaseQueryEnricher(postgresqlDbPoolConfig, new MetaDataProviderFactory().create(postgresqlDbPoolConfig))

  override val preparePostgresqlDDLs: List[String] = List(
    "CREATE TABLE people (id INT, name VARCHAR(40));",
    "INSERT INTO people (id, name) VALUES (1, 'John');",
    "CREATE TABLE types_test(t_time TIME, t_timestamp TIMESTAMP, t_timestamptz TIMESTAMPTZ, t_date DATE, " +
      "t_array INT[], t_boolean BOOLEAN, t_text TEXT);",
    "INSERT INTO types_test(t_time, t_timestamp, t_timestamptz, t_date, t_array, t_boolean, t_text) VALUES (" +
      "'08:00:00', '2024-08-12 10:00:00', '2024-08-12 09:00:00+01:00', '2024-08-12', '{1,2,3,4,5}', true, 'long text');"
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

  test("DatabaseQueryEnricherPostgresqlTest#implementation without cache") {
    val result = queryWithEnricher(
      "select * from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{id: Integer, name: String}]"
    )
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )
  }

  test(
    "DatabaseQueryEnricherPostgresqlTest#implementation without cache and with mixed lowercase and uppercase characters"
  ) {
    val result = queryWithEnricher(
      "select iD, NaMe from people where id = ?",
      Map("arg1" -> 1.asInstanceOf[AnyRef]),
      conn,
      service,
      "List[Record{id: Integer, name: String}]"
    )
    result shouldBe List(
      TypedMap(Map("name" -> "John", "id" -> 1))
    )
  }

  test("DatabaseQueryEnricherPostgresqlTest#implementation update query") {
    val query = "UPDATE people SET name = 'Don' where id = ?"
    updateWithEnricher(query, conn, Map("arg1" -> 1.asInstanceOf[AnyRef]), service)

    val queryResultSet = conn.prepareStatement("SELECT * FROM people WHERE id = 1").executeQuery()
    queryResultSet.next()
    queryResultSet.getObject("name") shouldBe "Don"
  }

  test("DatabaseQueryEnricherPostgresqlTest#type conversions") {
    import scala.jdk.CollectionConverters._
    val result = queryWithEnricher(
      "select * from types_test",
      Map(),
      conn,
      service,
      "List[Record{t_array: List[Unknown], t_boolean: Boolean, t_date: LocalDate, t_text: String, t_time: LocalTime, " +
        "t_timestamp: Instant, t_timestamptz: Instant}]"
    )

    result shouldBe List(
      TypedMap(
        Map(
          "t_boolean"     -> true,
          "t_timestamp"   -> ZonedDateTime.parse("2024-08-12T10:00:00+02:00").toInstant,
          "t_date"        -> LocalDate.parse("2024-08-12"),
          "t_array"       -> List(1, 2, 3, 4, 5).asJava,
          "t_text"        -> "long text",
          "t_time"        -> LocalTime.parse("08:00"),
          "t_timestamptz" -> ZonedDateTime.parse("2024-08-12T10:00:00+02:00").toInstant
        )
      )
    )
  }

}
