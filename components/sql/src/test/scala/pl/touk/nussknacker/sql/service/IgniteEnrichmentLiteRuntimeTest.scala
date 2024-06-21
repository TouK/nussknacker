package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.utils.ignite.WithIgniteDB
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.jdk.CollectionConverters._

class IgniteEnrichmentLiteRuntimeTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with WithIgniteDB
    with ValidatedValuesDetailedMessage {

  override val prepareIgniteDDLs: List[String] = List(
    s"""DROP TABLE CITIES IF EXISTS;""",
    s"""CREATE TABLE CITIES (ID INT primary key, NAME VARCHAR, COUNTRY VARCHAR, POPULATION BIGINT, FOUNDATION_DATE TIMESTAMP);""",
    s"INSERT INTO CITIES VALUES (1, 'Warszawa', 'Poland', 1793579, CURRENT_TIMESTAMP());",
    s"INSERT INTO CITIES VALUES (2, 'Lublin', 'Poland', 339784, CURRENT_TIMESTAMP());"
  )

  private val config = ConfigFactory.parseMap(
    Map(
      "config" -> Map(
        "databaseLookupEnricher" -> Map(
          "name"   -> "ignite-lookup-enricher",
          "dbPool" -> igniteConfigValues.asJava
        ).asJava
      ).asJava
    ).asJava
  )

  private val components = DatabaseEnricherComponentProvider.create(config)

  private val testScenarioRunner = TestScenarioRunner
    .liteBased()
    .withExtraComponents(components)
    .build()

  test("should enrich input ignite lookup enricher") {
    val process = ScenarioBuilder
      .streaming("")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "ignite-lookup-enricher",
        "output",
        "ignite-lookup-enricher",
        "Table"      -> "'CITIES'".spel,
        "Key column" -> "'ID'".spel,
        "Key value"  -> "#input".spel,
        "Cache TTL"  -> "".spel
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#output.NAME".spel)

    val validatedResult = testScenarioRunner.runWithData[Int, String](process, List(1))

    val resultList = validatedResult.validValue.successes
    resultList should have length 1
    resultList.head shouldEqual "Warszawa"
  }

}
