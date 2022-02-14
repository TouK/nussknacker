package pl.touk.nussknacker.processCounts.influxdb

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client.Identity
import sttp.client.monad.IdMonad

import java.time.Instant

class InfluxGeneratorSpec extends FunSuite with Matchers with PatientScalaFutures {

  import InfluxGenerator._

  //TODO: test generated query, not just shape of output
  test("Point in time query returns correct results") {

    val pointInTimeQuery = new PointInTimeQuery[Identity](_ => sampleInfluxOutput, "process1", "test", MetricsConfig())(IdMonad)

    pointInTimeQuery.query(Instant.now()) shouldBe Map(
      "start" -> (552855221L + 557871409L),
      "end" -> (412793677L + 414963365L)
    )
  }

  val sampleInfluxOutputRaw: String = """
    |    [
    |        {
    |          "name": "nodeCount",
    |          "tags": {
    |            "nodeId": "end",
    |            "slot": "0"
    |          },
    |          "columns": [
    |            "time",
    |            "nodeId",
    |            "count"
    |          ],
    |          "values": [
    |            [
    |              "2018-10-15T06:17:35Z",
    |              "end",
    |              412793677
    |            ]
    |          ]
    |        },
    |        {
    |          "name": "nodeCount",
    |          "tags": {
    |            "nodeId": "end",
    |            "slot": "1"
    |          },
    |          "columns": [
    |            "time",
    |            "nodeId",
    |            "count"
    |          ],
    |          "values": [
    |            [
    |              "2018-10-15T06:17:35Z",
    |              "end",
    |              414963365
    |            ]
    |          ]
    |        },
    |        {
    |          "name": "nodeCount",
    |          "tags": {
    |            "nodeId": "start",
    |            "slot": "0"
    |          },
    |          "columns": [
    |            "time",
    |            "nodeId",
    |            "count"
    |          ],
    |          "values": [
    |            [
    |              "2018-10-15T06:17:35Z",
    |              "start",
    |              552855221
    |            ]
    |          ]
    |        },
    |        {
    |          "name": "nodeCount",
    |          "tags": {
    |            "nodeId": "start",
    |            "slot": "1"
    |          },
    |          "columns": [
    |            "time",
    |            "nodeId",
    |            "count"
    |          ],
    |          "values": [
    |            [
    |              "2018-10-15T06:17:35Z",
    |              "start",
    |              557871409
    |            ]
    |          ]
    |        }
    |      ]
  """.stripMargin

  val sampleInfluxOutput: List[InfluxSeries] = CirceUtil.decodeJsonUnsafe[List[InfluxSeries]](sampleInfluxOutputRaw, "failed to decode series")

}
