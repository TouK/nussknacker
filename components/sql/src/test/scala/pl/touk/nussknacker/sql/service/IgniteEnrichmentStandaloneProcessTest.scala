package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.sql.utils._
import pl.touk.nussknacker.sql.utils.ignite.WithIgniteDB

import scala.collection.JavaConverters._

class IgniteEnrichmentStandaloneProcessTest extends FunSuite with Matchers with StandaloneProcessTest with BeforeAndAfterAll
  with WithIgniteDB {

  override val contextPreparer: LiteEngineRuntimeContextPreparer = LiteEngineRuntimeContextPreparer.noOp

  override val prepareIgniteDDLs: List[String] = List(
    s"""DROP TABLE CITIES IF EXISTS;""",
    s"""CREATE TABLE CITIES (ID INT primary key, NAME VARCHAR, COUNTRY VARCHAR, POPULATION BIGINT, FOUNDATION_DATE TIMESTAMP);""",
    s"INSERT INTO CITIES VALUES (1, 'Warszawa', 'Poland', 1793579, CURRENT_TIMESTAMP());",
    s"INSERT INTO CITIES VALUES (2, 'Lublin', 'Poland', 339784, CURRENT_TIMESTAMP());"
  )

  private val config = ConfigFactory.parseMap(
    Map(
      "components" -> Map(
        "databaseEnricher" -> Map(
          "config" -> Map(
            "databaseLookupEnricher" -> Map(
              "name" -> "ignite-lookup-enricher",
              "dbPool" -> igniteConfigValues.asJava
            ).asJava
          ).asJava
        ).asJava
      ).asJava
    ).asJava
  )

  override val modelData: LocalModelData = LocalModelData(config, new RequestResponseConfigCreator)

  test("should enrich input ignite lookup enricher") {
    val process = EspProcessBuilder
      .id("")
      .source("request", "request")
      .enricher("ignite-lookup-enricher", "output", "ignite-lookup-enricher",
        "Table" -> "'CITIES'",
        "Key column" -> "'ID'",
        "Key value" -> "#input.id",
        "Cache TTL" -> ""
      )
      .emptySink("response", "response", "name" -> "#output.NAME", "count" -> "")

    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'valid

    val resultList = validatedResult.getOrElse(throw new AssertionError())
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "Warszawa"
    }
  }

}
