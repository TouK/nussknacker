package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.utils._
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class DatabaseLookupLiteRuntimeTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with WithHsqlDB
    with ValidatedValuesDetailedMessage {

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

  private val components = DatabaseEnricherComponentProvider.create(config)

  private val testScenarioRunner = TestScenarioRunner
    .liteBased()
    .withExtraComponents(components)
    .build()

  test("should enrich input with data from db") {
    val process = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "sql-lookup-enricher",
        "output",
        "sql-lookup-enricher",
        "Table"      -> "'PERSONS'",
        "Key column" -> "'ID'",
        "Key value"  -> "#input",
        "Cache TTL"  -> ""
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#output")

    val validatedResult = testScenarioRunner.runWithData[Int, AnyRef](process, List(1))

    val resultList = validatedResult.validValue.successes
    resultList should have length 1
    // TODO
    resultList.head shouldEqual "John"
  }

  test("should enrich input with table with lower cases in column names") {
    val process = ScenarioBuilder
      .requestResponse("test scenario")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "sql-lookup-enricher",
        "output",
        "sql-lookup-enricher",
        "Table"      -> "'PERSONS_LOWER'",
        "Key column" -> "'id'",
        "Key value"  -> "#input",
        "Cache TTL"  -> ""
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#output")

    val validatedResult = testScenarioRunner.runWithData[Int, String](process, List(1))

    val resultList = validatedResult.validValue.successes
    resultList should have length 1
    // TODO
    resultList.head shouldEqual "John"
  }

}
