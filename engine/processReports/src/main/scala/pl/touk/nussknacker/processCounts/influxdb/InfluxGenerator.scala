package pl.touk.nussknacker.processCounts.influxdb

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

import sttp.client.{NothingT, SttpBackend}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

private[influxdb] class InfluxGenerator(config: InfluxConfig, env: String = "test")(implicit backend: SttpBackend[Future, Nothing, NothingT]) extends LazyLogging {

  import InfluxGenerator._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val influxClient = new SimpleInfluxClient(config)

  //TODO: query below should work better even in case of restarts, however for large processes/time ranges it can be *really* slow (at least on influx we use...)
  //select sum(diff) from (select non_negative_difference(value) as diff from "$metricName.count" where process = '$processName' and env = '$env' and time > $from and time < $to group by slot) group by action
  def query(processName: String, dateFrom: Option[LocalDateTime], dateTo: LocalDateTime, config: MetricsConfig): Future[Map[String, Long]] = {
    val pointInTimeQuery = new PointInTimeQuery(influxClient.query, processName, env, config)

    for {
      valuesAtEnd <- pointInTimeQuery.query(dateTo)
      valuesAtStart <- dateFrom.map(pointInTimeQuery.query)
        .getOrElse(Future.successful(Map[String, Long]()))
    } yield valuesAtEnd.map {
      case (key, value) => key -> (value - valuesAtStart.getOrElse(key, 0L))
    }
  }



  def detectRestarts(processName: String, dateFrom: LocalDateTime, dateTo: LocalDateTime, config: MetricsConfig): Future[List[LocalDateTime]] = {
    val from = toEpochSeconds(dateFrom)
    val to = toEpochSeconds(dateTo)
    val queryString =
      //TODO: is it always correct? Will it be performant enough for large (many days) ranges?
      s"""SELECT derivative(${config.countField}) FROM "${config.sourceCountMetric}" WHERE
         | "${config.processTag}" = '$processName' AND ${config.envTag} = '$env'
         | AND time >= ${from}s and time < ${to}s GROUP BY ${config.slotTag}""".stripMargin
    influxClient.query(queryString).map { series =>
      series.headOption.map(readRestartsFromSourceCounts).getOrElse(List())
    }
  }

  private def readRestartsFromSourceCounts(sourceCounts: InfluxSerie) : List[LocalDateTime] = {
    val restarts = sourceCounts.values.collect {
      case (date:String)::(derivative:BigDecimal)::Nil if derivative < 0 => parseInfluxDate(date)
    }
    restarts
  }

  private def parseInfluxDate(date:String) : LocalDateTime =
    ZonedDateTime.parse(date, DateTimeFormatter.ISO_ZONED_DATE_TIME).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime

  def close(): Unit = {
    influxClient.close()
  }


}

object InfluxGenerator {

  private[influxdb] def toEpochSeconds(d: LocalDateTime): Long = {
    d.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli / 1000
  }

  //influx cannot give us result for "give me value nearest in time to t1", so we try to do it by looking for
  //last point before t1 and first after t1.
  // TODO: probably we should just take one of them, but the one which is closer to t1?
  class PointInTimeQuery(query: String => Future[List[InfluxSerie]], processName: String, env: String, config: MetricsConfig)(implicit ec: ExecutionContext) extends LazyLogging {

    //two hour window is for possible delays in sending metrics from taskmanager to jobmanager (or upd sending problems...)
    //it's VERY unclear how large it should be. If it's too large, we may overlap with end and still generate
    //bad results...
    def query(date: LocalDateTime): Future[Map[String, Long]] = {
      def query(timeCondition: String, aggregateFunction: String) =
        s"""select ${config.nodeIdTag} as nodeId, $aggregateFunction(${config.countField}) as count
           | from "${config.nodeCountMetric}" where ${config.processTag} = '$processName'
           | and ${timeCondition} and ${config.envTag} = '$env' group by ${config.slotTag}, ${config.nodeIdTag} fill(0)""".stripMargin

      val around = toEpochSeconds(date)
      for {
        valuesBefore <- retrieveOnlyResultFromActionValueQuery(query(timeCondition = s"time <= ${around}s and time > ${around}s - 1h", aggregateFunction = "last"))
        valuesAfter <- retrieveOnlyResultFromActionValueQuery(query(timeCondition = s"time >= ${around}s and time < ${around}s + 1h", aggregateFunction = "first"))
      } yield valuesBefore ++ valuesAfter
    }

    //see InfluxGeneratorSpec for influx return format...
    def retrieveOnlyResultFromActionValueQuery(queryString: String): Future[Map[String, Long]] = {
      val groupedResults = query(queryString).map { seriesList =>
        seriesList.map { oneSeries =>
          //in case of our queries we know there will be only one result (we use only first/last aggregations), rest will be handled by aggregations
          val firstResult = oneSeries.toMap.headOption.getOrElse(Map())
          (firstResult.getOrElse("nodeId", "UNKNOWN").asInstanceOf[String], firstResult.getOrElse("count", 0L).asInstanceOf[Number].longValue())
        }.groupBy(_._1).mapValues(_.map(_._2).sum)
      }
      groupedResults.foreach {
        evaluated => logger.debug(s"Query: $queryString retrieved grouped results: $evaluated")
      }
      groupedResults
    }
  }

}
