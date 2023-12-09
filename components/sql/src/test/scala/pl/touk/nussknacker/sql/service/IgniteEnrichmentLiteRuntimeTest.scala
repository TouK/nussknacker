package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside.inside
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.sql.DatabaseEnricherComponentProvider
import pl.touk.nussknacker.sql.utils._
import pl.touk.nussknacker.sql.utils.ignite.WithIgniteDB

import scala.jdk.CollectionConverters._

class IgniteEnrichmentLiteRuntimeTest
    extends AnyFunSuite
    with Matchers
    with LiteRuntimeTest
    with BeforeAndAfterAll
    with WithIgniteDB {

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

  private val components = new DatabaseEnricherComponentProvider().create(config, ProcessObjectDependencies.empty)

  override val modelData: LocalModelData = LocalModelData(config, new RequestResponseConfigCreator, components)

  test("should enrich input ignite lookup enricher") {
    val process = ScenarioBuilder
      .streaming("")
      .source("request", "request")
      .enricher(
        "ignite-lookup-enricher",
        "output",
        "ignite-lookup-enricher",
        "Table"      -> "'CITIES'",
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
      resp.name shouldEqual "Warszawa"
    }
  }

}
