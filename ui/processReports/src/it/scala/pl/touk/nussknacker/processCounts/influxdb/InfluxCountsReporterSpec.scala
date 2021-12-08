package pl.touk.nussknacker.processCounts.influxdb

import com.dimafeng.testcontainers.{ForAllTestContainer, InfluxDBContainer}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.processCounts.{CannotFetchCountsError, ExecutionCount, RangeCount}
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend}

import java.time.Duration._
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

class InfluxCountsReporterSpec extends FunSuite with ForAllTestContainer with TableDrivenPropertyChecks with VeryPatientScalaFutures with Matchers {

  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend.usingConfig(new DefaultAsyncHttpClientConfig.Builder().build())

  override val container: InfluxDBContainer = InfluxDBContainer()

  private val startTime = Instant.now()

  private implicit class RichInstant(instant: Instant) {
    def plusHours(count: Int): Instant = instant.plus(ofHours(count))
    def plusMinutes(count: Int): Instant = instant.plus(ofMinutes(count))
    def minusHours(count: Int): Instant = instant.minus(ofHours(count))
    def minusMinutes(count: Int): Instant = instant.minus(ofMinutes(count))
  }

  private val env = "testEnv"

  test("invokes counts for point in time data") {

    val process = "myProcess-1"

    val data = new InfluxData(MetricsConfig())

    data.writePointForCount(process, "node1", 1, startTime.minusSeconds(3))
    data.writePointForCount(process, "node1", 10, startTime)
    data.writePointForCount(process, "node2", 20, startTime)

    val results = data.reporter(QueryMode.OnlySingleDifference).prepareRawCounts(process, ExecutionCount(startTime)).futureValue
    results("node1") shouldBe Some(10)
    results("node2") shouldBe Some(20)
    results("node3") shouldBe None


  }

  test("invokes query for date range") {

    val process = "myProcess-2"

    val data = new InfluxData(MetricsConfig())

    data.writePointForCount(process, "node1", 1, startTime.minusMinutes(62))
    data.writePointForCount(process, "node1", 1, startTime.minusMinutes(59))
    
    data.writePointForCount(process, "node1", 10, startTime.plusHours(2).minusMinutes(1))
    data.writePointForCount(process, "node1", 10, startTime.plusHours(2))

    data.writePointForCount(process, "node2", 20, startTime.minusMinutes(45))
    data.writePointForCount(process, "node2", 20, startTime.minusMinutes(43))
    data.writePointForCount(process, "node2", 50, startTime.plusHours(2).minusMinutes(1))
    data.writePointForCount(process, "node2", 50, startTime.plusHours(2))


    forQueryModes(QueryMode.values) { mode:QueryMode.Value =>
      val results = data.reporter(mode)
        .prepareRawCounts(process, RangeCount(startTime.minusHours(1), startTime.plusHours(2))).futureValue
      results("node1") shouldBe Some(9)
      results("node2") shouldBe Some(30)
      results("node3") shouldBe None
    }
  }

  test("should detect restarts one SingleDifference mode") {
    val process = "myProcess-3"

    val data = new InfluxData(MetricsConfig())

    data.writePointForCount(process, "node1", 15, startTime.minusHours(1).plusMinutes(2))
    data.writePointForCount(process, "node1", 25, startTime.minusHours(1).plusMinutes(20))

    data.writePointForCount(process, "node1", 10, startTime.minusMinutes(1))
    data.writePointForCount(process, "node1", 15, startTime.plusMinutes(1))

    data.writePointForCount(process, "node1", 25, startTime.plusHours(2).minusMinutes(1))

    data.reporter(QueryMode.OnlySingleDifference)
      .prepareRawCounts(process, RangeCount(startTime.minusHours(1), startTime.plusHours(2)))
      .failed.futureValue shouldBe
      CannotFetchCountsError.restartsDetected(List(startTime.minusMinutes(1)))

    forQueryModes(QueryMode.values - QueryMode.OnlySingleDifference) { mode =>
      val value = data.reporter(mode)
        .prepareRawCounts(process, RangeCount(startTime.minusHours(1), startTime.plusHours(2)))
        .futureValue
      value("node1") shouldBe Some(10 + 15)

    }
  }

  private def forQueryModes(queryModes: Set[QueryMode.Value])(fun: QueryMode.Value => Assertion): Unit = {
    forAll(Table[QueryMode.Value]("mode", queryModes.toArray:_*))(fun)
  }

  class InfluxData(config: MetricsConfig) {

    private val influxDB = InfluxDBFactory.connect(container.url, container.username, container.password)

    def reporter(queryMode: QueryMode.Value) = new InfluxCountsReporter(env,
      InfluxConfig(container.url + "/query", Option(container.username), Option(container.password), container.database, queryMode, Some(config))
    )

    influxDB.setDatabase(container.database)
    influxDB.disableBatch()

    def writePointForCount(processName: String,
                           nodeName: String,
                           value: Long,
                           time: Instant,
                           slot: Int = 0): Unit = {
      def savePoint(measurement: String): Unit = {
        influxDB.write(Point
          .measurement(measurement)
          .addField(config.countField, value)
          .time(time.toEpochMilli, TimeUnit.MILLISECONDS)
          .tag(config.envTag, env)
          .tag(config.nodeIdTag, nodeName)
          .tag(config.scenarioTag, processName)
          .tag(config.slotTag, slot.toString)
          .build())
      }
      savePoint(config.nodeCountMetric)
      savePoint(config.sourceCountMetric)
    }

  }

}


