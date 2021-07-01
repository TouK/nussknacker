package pl.touk.nussknacker.sql.service

import com.typesafe.config.ConfigFactory
import org.scalatest.Inside.inside
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.standalone.api.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.sql.utils._

import scala.collection.JavaConverters._

class DatabaseLookupStandaloneProcessTest extends FunSuite with Matchers with StandaloneProcessTest with BeforeAndAfterAll with WithDB {
  override val modelData: LocalModelData = LocalModelData(config, new StandaloneConfigCreator)
  override val contextPreparer: StandaloneContextPreparer = new StandaloneContextPreparer(NoOpMetricsProvider)
  override val prepareDbDDLs: List[String] = List(
    "CREATE TABLE persons (id INT, name VARCHAR(40));",
    "INSERT INTO persons (id, name) VALUES (1, 'John')",
    "CREATE TABLE persons_lower (\"id\" INT, \"name\" VARCHAR(40));",
    "INSERT INTO persons_lower VALUES (1, 'John')"
  )
  private val config = ConfigFactory.parseMap(Map(
    "sqlEnricherDbPool" -> Map(
      "driverClassName" -> dbConf.driverClassName,
      "username" -> dbConf.username,
      "password" -> dbConf.password,
      "url" -> dbConf.url
    ).asJava
  ).asJava)

  test("should enrich input with data from db") {
    val process = EspProcessBuilder
      .id("")
      .exceptionHandlerNoParams()
      .source("request", "request")
      .enricher("sql-lookup-enricher", "output", "sql-lookup-enricher",
        "Table" -> "'PERSONS'",
        "Key column" -> "'ID'",
        "Key value" -> "#input.id",
        "Cache TTL" -> ""
      )
      .emptySink("response", "response", "name" -> "#output.NAME")

    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'right

    val resultList = validatedResult.right.get
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "John"
    }
  }

  test("should enrich input with table with lower cases in column names") {
    val process = EspProcessBuilder
      .id("")
      .exceptionHandlerNoParams()
      .source("request", "request")
      .enricher("sql-lookup-enricher", "output", "sql-lookup-enricher",
        "Table" -> "'PERSONS_LOWER'",
        "Key column" -> "'id'",
        "Key value" -> "#input.id",
        "Cache TTL" -> ""
      )
      .emptySink("response", "response", "name" -> "#output.name")

    val validatedResult = runProcess(process, StandaloneRequest(1))
    validatedResult shouldBe 'right

    val resultList = validatedResult.right.get
    resultList should have length 1

    inside(resultList.head) {
      case resp: StandaloneResponse =>
        resp.name shouldEqual "John"
    }
  }

}
