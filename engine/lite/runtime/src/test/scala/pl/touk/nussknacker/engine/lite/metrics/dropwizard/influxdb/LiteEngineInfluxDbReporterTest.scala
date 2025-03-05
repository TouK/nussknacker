package pl.touk.nussknacker.engine.lite.metrics.dropwizard.influxdb

import com.typesafe.config.{Config, ConfigFactory}
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import io.dropwizard.metrics5.influxdb.InfluxDbReporter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class LiteEngineInfluxDbReporterTest extends AnyFunSuite with Matchers {

  test("reporter should be created for correct format") {
    val config = ConfigFactory.parseString("""influx {
        |  url: "http://localhost",
        |  database: "fooDb"
        |}""".stripMargin)
    createAndRunReporterIfConfigured(config) shouldBe defined
  }

  test("reporter shouldn't be created if config has illegal format") {
    createAndRunReporterIfConfigured(ConfigFactory.empty()) shouldBe empty

    val configWithInvalidUrl = ConfigFactory.parseString("""influx {
        |  url: "/urlWithMissingScheme",
        |  database: "fooDb"
        |}""".stripMargin)
    createAndRunReporterIfConfigured(configWithInvalidUrl) shouldBe empty
  }

  private def createAndRunReporterIfConfigured(config: Config): Option[InfluxDbReporter] = {
    val metricName = new MetricName("fooKey", Map.empty[String, String].asJava)
    StubbedLiteEngineInfluxDbReporter.createAndRunReporterIfConfigured(new MetricRegistry, metricName, config)
  }

  object StubbedLiteEngineInfluxDbReporter extends LiteEngineInfluxDbReporter {

    override protected def createAndRunReporter(
        metricRegistry: MetricRegistry,
        prefix: MetricName,
        influxSenderConfig: InfluxSenderConfig
    ): InfluxDbReporter = null

  }

}
