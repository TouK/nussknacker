package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside.inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.utils._

import scala.jdk.CollectionConverters._

class DatabaseLookupLiteRuntimeTest
    extends AnyFunSuite
    with Matchers
    with LiteRuntimeTest
    with BeforeAndAfterAll
    with WithHsqlDB {

  override val prepareHsqlDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')",
    "CREATE TABLE persons_lower (\"id\" INT, \"name\" VARCHAR(40));",
    "INSERT INTO persons_lower VALUES (1, 'John')"
  )

  private val config = ConfigFactory.parseMap(
    Map(
      "config" -> Map(
        "databaseLookupEnricher" -> Map(
          "name"   -> "sql-lookup-enricher",
          "dbPool" -> hsqlConfigValues.asJava
        ).asJava,
        "databaseQueryEnricher" -> Map(
          "name"   -> "sql-query-enricher",
          "dbPool" -> hsqlConfigValues.asJava
        ).asJava
      ).asJava
    ).asJava
  )

  private val components = new DatabaseEnricherComponentProvider().create(config, ProcessObjectDependencies.empty)

  override val modelData: LocalModelData =
    LocalModelData(ConfigFactory.empty(), new RequestResponseConfigCreator, components)

  test("should enrich input with data from db") {
    val process = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", "request")
      .enricher(
        "sql-lookup-enricher",
        "output",
        "sql-lookup-enricher",
        "Table"      -> "'PERSONS'",
        "Key column" -> "'ID'",
        "Key value"  -> "#input.id",
        "Cache TTL"  -> ""
      )
      .emptySink("response", "response", "name" -> "#output.NAME", "count" -> "")

    val validatedResult = runProcess(process, TestRequest(1))
    validatedResult shouldBe Symbol("valid")

    val resultList = validatedResult.getOrElse(throw new AssertionError())
    resultList should have length 1

    inside(resultList.head) { case resp: TestResponse =>
      resp.name shouldEqual "John"
    }
  }

  test("should enrich input with table with lower cases in column names") {
    val process = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", "request")
      .enricher(
        "sql-lookup-enricher",
        "output",
        "sql-lookup-enricher",
        "Table"      -> "'PERSONS_LOWER'",
        "Key column" -> "'id'",
        "Key value"  -> "#input.id",
        "Cache TTL"  -> ""
      )
      .emptySink("response", "response", "name" -> "#output.name", "count" -> "")

    val validatedResult = runProcess(process, TestRequest(1))
    validatedResult shouldBe Symbol("valid")

    val resultList = validatedResult.getOrElse(throw new AssertionError())
    resultList should have length 1

    inside(resultList.head) { case resp: TestResponse =>
      resp.name shouldEqual "John"
    }
  }

}
