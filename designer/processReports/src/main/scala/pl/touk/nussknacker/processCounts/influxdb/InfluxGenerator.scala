package pl.touk.nussknacker.processCounts.influxdb

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import sttp.client3.SttpBackend
import sttp.monad.MonadError
import sttp.monad.syntax._

import java.time.{Instant, ZonedDateTime}
import java.time.format.DateTimeFormatter
import scala.language.higherKinds

private[influxdb] class InfluxGenerator[F[_]](config: InfluxConfig, env: String)(implicit backend: SttpBackend[F, Any])
    extends LazyLogging {

  import InfluxGenerator._

  private implicit val monadError: MonadError[F] = backend.responseMonad

  private val influxClient = new SimpleInfluxClient(config)

  def queryBySingleDifference(
      processName: ProcessName,
      dateFrom: Option[Instant],
      dateTo: Instant,
      config: MetricsConfig
  ): F[Map[String, Long]] = {
    val pointInTimeQuery = new PointInTimeQuery(influxClient.query, processName, env, config)

    for {
      valuesAtEnd <- pointInTimeQuery.query(dateTo)
      valuesAtStart <- dateFrom
        .map(pointInTimeQuery.query)
        .getOrElse(monadError.unit(Map[String, Long]()))
    } yield valuesAtEnd.map { case (key, value) =>
      key -> (value - valuesAtStart.getOrElse(key, 0L))
    }
  }

  def queryBySumOfDifferences(
      processName: ProcessName,
      dateFrom: Instant,
      dateTo: Instant,
      config: MetricsConfig
  ): F[Map[String, Long]] = {
    val query = s"""select sum(diff) as count from (SELECT non_negative_difference("${config.countField}") AS diff
     FROM "${config.nodeCountMetric}"
     WHERE ${config.envTag} = '$env' AND ${config.scenarioTag} = '$processName'
     AND time > ${dateFrom.getEpochSecond}s AND time < ${dateTo.getEpochSecond}s
     GROUP BY ${config.nodeIdTag}, ${config.additionalGroupByTags.mkString(",")}) group by ${config.nodeIdTag}"""
    InfluxGenerator.retrieveOnlyResultFromActionValueQuery(config, influxClient.query, query)
  }

  def detectRestarts(
      processName: ProcessName,
      dateFrom: Instant,
      dateTo: Instant,
      config: MetricsConfig
  ): F[List[Instant]] = {
    val from = dateFrom.getEpochSecond
    val to   = dateTo.getEpochSecond
    val queryString =
      s"""SELECT diff FROM (
         |  SELECT difference(${config.countField}) as diff FROM "${config.sourceCountMetric}" WHERE
         | "${config.scenarioTag}" = '$processName' AND ${config.envTag} = '$env'
         | AND time >= ${from}s and time < ${to}s GROUP BY ${config.additionalGroupByTags.mkString(
          ","
        )}, ${config.nodeIdTag}) where diff < 0 """.stripMargin
    influxClient.query(queryString).map { series =>
      series.headOption.map(readRestartsFromSourceCounts).getOrElse(List())
    }
  }

  private def readRestartsFromSourceCounts(sourceCounts: InfluxSeries): List[Instant] = {
    val restarts = sourceCounts.values.collect { case (date: String) :: (derivative: BigDecimal) :: Nil =>
      parseInfluxDate(date)
    }
    restarts
  }

  private def parseInfluxDate(date: String): Instant =
    ZonedDateTime.parse(date, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant

}

object InfluxGenerator extends LazyLogging {

  // see InfluxGeneratorSpec for influx return format...
  def retrieveOnlyResultFromActionValueQuery[F[_]: MonadError](
      config: MetricsConfig,
      invokeQuery: String => F[List[InfluxSeries]],
      queryString: String
  ): F[Map[String, Long]] = {

    val groupedResults = invokeQuery(queryString).map { seriesList =>
      seriesList
        .map { oneSeries =>
          // in case of our queries we know there will be only one result (we use only first/last aggregations), rest will be handled by aggregations
          val firstResult = oneSeries.toMap.headOption.getOrElse(Map())
          (
            oneSeries.tags.getOrElse(Map.empty).getOrElse(config.nodeIdTag, "UNKNOWN"),
            firstResult.getOrElse("count", 0L).asInstanceOf[Number].longValue()
          )
        }
        .groupBy(_._1)
        .mapValuesNow(_.map(_._2).sum)
    }
    groupedResults.map { evaluated =>
      logger.debug(s"Query: $queryString retrieved grouped results: $evaluated")
    }
    groupedResults
  }

  // influx cannot give us result for "give me value nearest in time to t1", so we try to do it by looking for
  // last point before t1 and first after t1.
  // TODO: probably we should just take one of them, but the one which is closer to t1?
  class PointInTimeQuery[F[_]: MonadError](
      invokeQuery: String => F[List[InfluxSeries]],
      processName: ProcessName,
      env: String,
      config: MetricsConfig
  ) extends LazyLogging {

    // two hour window is for possible delays in sending metrics from taskmanager to jobmanager (or upd sending problems...)
    // it's VERY unclear how large it should be. If it's too large, we may overlap with end and still generate
    // bad results...
    def query(date: Instant): F[Map[String, Long]] = {
      def query(timeCondition: String, aggregateFunction: String) =
        s"""select ${config.nodeIdTag} as nodeId, $aggregateFunction(${config.countField}) as count
           | from "${config.nodeCountMetric}" where ${config.scenarioTag} = '$processName'
           | and $timeCondition and ${config.envTag} = '$env' group by ${config.additionalGroupByTags.mkString(
            ","
          )}, ${config.nodeIdTag} fill(0)""".stripMargin

      val around = date.getEpochSecond
      for {
        valuesBefore <- retrieveOnlyResultFromActionValueQuery(
          config,
          invokeQuery,
          query(timeCondition = s"time <= ${around}s and time > ${around}s - 1h", aggregateFunction = "last")
        )
        valuesAfter <- retrieveOnlyResultFromActionValueQuery(
          config,
          invokeQuery,
          query(timeCondition = s"time >= ${around}s and time < ${around}s + 1h", aggregateFunction = "first")
        )
      } yield valuesBefore ++ valuesAfter
    }

  }

}
