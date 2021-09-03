package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.standalone.api.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.sql.utils._

import scala.collection.JavaConverters._

class IgniteEnrichmentStandaloneProcessTest extends FunSuite with Matchers with StandaloneProcessTest with BeforeAndAfterAll
  with WithIgniteDB {

  override val contextPreparer: StandaloneContextPreparer = new StandaloneContextPreparer(NoOpMetricsProvider)

  override val prepareIgniteDDLs: List[String] = List(
    s"""DROP TABLE CITIES IF EXISTS;""",
    s"""CREATE TABLE CITIES (ID INT primary key, NAME VARCHAR, COUNTRY VARCHAR, POPULATION BIGINT);""",
    s"INSERT INTO CITIES VALUES (1, 'Warszawa', 'Poland', 123);",
    s"INSERT INTO CITIES VALUES (2, 'Lublin', 'Poland', 234);"
  )

  private val config = ConfigFactory.parseMap(
    Map(
      "components" -> Map(
        "databaseEnricher" -> Map(
          "providerType" -> "igniteEnricher",
          "config" -> Map(
            "databaseLookupEnricher" -> Map(
              "name" -> "ignite-lookup-enricher",
              "dbPool" -> igniteConfigValues.asJava
            ).asJava,
            "databaseQueryEnricher" -> Map(
              "name" -> "ignite-query-enricher",
              "dbPool" -> igniteConfigValues.asJava
            ).asJava
          ).asJava
        ).asJava
      ).asJava
    ).asJava
  )

  override val modelData: LocalModelData = LocalModelData(config, new StandaloneConfigCreator)

  test("should enrich input ignite lookup enricher") {
    val process = EspProcessBuilder
      .id("")
      .exceptionHandlerNoParams()
      .source("request", "request")
      .enricher("ignite-lookup-enricher", "output", "ignite-lookup-enricher",
        "Table" -> "'CITIES'",
        "Key column" -> "'ID'",
        "Key value" -> "#input.id",
        "Cache TTL" -> ""
      )
      //      TODO: coś lepszego niż tylko pole name w testach
      .emptySink("response", "response", "name" -> "#output.NAME")

    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'right

    val resultList = validatedResult.right.get
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "Warszawa"
    }
  }

  test("should enrich simple with ignite query enricher with simple select query") {
    val process = EspProcessBuilder
      .id("")
      .exceptionHandlerNoParams()
      .source("request", "request")
      .enricher("ignite-query-enricher", "output", "ignite-query-enricher",
        "Result strategy" -> "'Single result'",
        "Query" -> "'SELECT * FROM CITIES WHERE NAME=?'",
        "Cache TTL" -> "",
        "arg1" -> s"'Lublin'"
      )
//    TODO: coś lepszego niż tylko pole name w testach
      .emptySink("response", "response", "name" -> "#output.NAME")

    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'right

    val resultList = validatedResult.right.get
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "Lublin"
    }
  }

  test("should enrich input with ignite query enricher with group by query") {
    val process = EspProcessBuilder
      .id("")
      .exceptionHandlerNoParams()
      .source("request", "request")
      .enricher("ignite-query-enricher", "output", "ignite-query-enricher",
        "Result strategy" -> "'Single result'",
        "Query" -> "'SELECT COUNTRY, MAX(POPULATION) AS MAX_POPULATION FROM CITIES GROUP BY COUNTRY'",
        "Cache TTL" -> ""
      )
//    TODO: coś lepszego niż tylko pole name w testach
      .emptySink("response", "response", "name" -> "#output.MAX_POPULATION.toString()")
    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'right

    val resultList = validatedResult.right.get
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "234"
    }
  }
}
