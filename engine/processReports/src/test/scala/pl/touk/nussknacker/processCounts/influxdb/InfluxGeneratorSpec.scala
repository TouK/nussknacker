package pl.touk.nussknacker.processCounts.influxdb

import java.time.LocalDateTime

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.processCounts.influxdb.InfluxGenerator.{InfluxSerie, PointInTimeQuery}
import argonaut._
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future

class InfluxGeneratorSpec extends FunSuite with Matchers with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global
  import InfluxGenerator._

  //TODO: test generated query, not just shape of output
  test("Point in time query returns correct results") {

    val pointInTimeQuery = new PointInTimeQuery(_ => Future.successful(sampleInfluxOutput), "process1", "nodeCount", "test")

    pointInTimeQuery.query(LocalDateTime.now()).futureValue shouldBe Map(
      "start" -> (552855221L + 557871409L),
      "end" -> (412793677L + 414963365L)
    )
  }

  val sampleInfluxOutputRaw: String = """
    |    [
    |        {
    |          "name": "nodeCount.count",
    |          "tags": {
    |            "action": "end",
    |            "slot": "0"
    |          },
    |          "columns": [
    |            "time",
    |            "action",
    |            "value"
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
    |          "name": "nodeCount.count",
    |          "tags": {
    |            "action": "end",
    |            "slot": "1"
    |          },
    |          "columns": [
    |            "time",
    |            "action",
    |            "value"
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
    |          "name": "nodeCount.count",
    |          "tags": {
    |            "action": "start",
    |            "slot": "0"
    |          },
    |          "columns": [
    |            "time",
    |            "action",
    |            "value"
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
    |          "name": "nodeCount.count",
    |          "tags": {
    |            "action": "start",
    |            "slot": "1"
    |          },
    |          "columns": [
    |            "time",
    |            "action",
    |            "value"
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

  val sampleInfluxOutput: List[InfluxSerie] = sampleInfluxOutputRaw.decodeOption[List[InfluxSerie]].get

}
