package pl.touk.nussknacker.sql.service

import com.dimafeng.testcontainers.ForAllTestContainer
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.lite.util.test.LiteTestScenarioRunner._
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.utils.WithCassandraDB
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import java.util
import scala.jdk.CollectionConverters._

class CassandraEnrichmentLiteRuntimeTest
    extends AnyFunSuite
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with WithCassandraDB
    with ForAllTestContainer
    with ValidatedValuesDetailedMessage {

  val cyclistId = "someText"

  val insertStatements = List(
    s"INSERT INTO cycling.cyclist_alt_stats (id, lastname, age) VALUES ('${cyclistId}-1', 'Kowalski-1', 48);",
    s"INSERT INTO cycling.cyclist_alt_stats (id, lastname, age) VALUES ('$cyclistId', 'Kowalski', 49);",
    s"INSERT INTO cycling.cyclist_alt_stats (id, lastname, age) VALUES ('${cyclistId}1', 'Kowalski2', 50);",
  )

  override val prepareCassandraSqlDDLs: List[String] = List(
    "CREATE KEYSPACE cycling WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };",
    "CREATE TABLE cycling.cyclist_alt_stats (id text PRIMARY KEY, lastname text, age int);",
  ) ++ insertStatements

  override protected def afterEach(): Unit = {
    val cleanupStatements = List(
      "TRUNCATE TABLE cycling.cyclist_alt_stats;",
    ) ++ insertStatements
    cleanupStatements.foreach { ddlStr =>
      val ddlStatement = conn.prepareStatement(ddlStr)
      try ddlStatement.execute()
      finally ddlStatement.close()
    }
  }

  private val config = ConfigFactory.parseMap(
    Map(
      "config" -> Map(
        "databaseLookupEnricher" -> Map(
          "name"   -> "cassandra-lookup-enricher",
          "dbPool" -> cassandraSqlConfigValues.asJava
        ).asJava
      ).asJava
    ).asJava
  )

  private val components = DatabaseEnricherComponentProvider.create(config)

  private val testScenarioRunner = TestScenarioRunner
    .liteBased()
    .withExtraComponents(components)
    .build()

  test("should enrich input cassandra lookup enricher") {
    val process = ScenarioBuilder
      .streaming("")
      .source("request", TestScenarioRunner.testDataSource)
      .enricher(
        "cassandra-lookup-enricher",
        "output",
        "cassandra-lookup-enricher",
        "Table"      -> "'cyclist_alt_stats'".spel,
        "Key column" -> "'id'".spel,
        "Key value"  -> "#input".spel,
        "Cache TTL"  -> "".spel
      )
      .emptySink("response", TestScenarioRunner.testResultSink, "value" -> "#output".spel)

    val validatedResult = testScenarioRunner.runWithData[String, AnyRef](process, List(cyclistId))

    val resultList = validatedResult.validValue.successes
    resultList should have length 1
    val resultScalaMap = resultList.head.asInstanceOf[util.HashMap[String, AnyRef]].asScala

    resultScalaMap.get("id").shouldEqual(Some(cyclistId))
    resultScalaMap.get("lastname").shouldEqual(Some("Kowalski"))
    resultScalaMap.get("age").shouldEqual(Some(49))
  }

}
