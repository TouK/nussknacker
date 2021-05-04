package pl.touk.nussknacker.engine.standalone.http.metrics.dropwizard.influxdb

import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.influxdb._
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import sttp.client._

import java.lang
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object InfluxDbHttpReporter {

  def build(metricRegistry: MetricRegistry, conf: InfluxSenderConfig): InfluxDbReporter =
    InfluxDbReporter.forRegistry(metricRegistry)
      .prefixedWith(
        conf.prefix.map(MetricName.build(_)).getOrElse(MetricName.empty())
          .tagged("host", conf.host)
          .tagged("env", conf.environment)
          .tagged("type", conf.`type`)
      ).build(new InfluxDbHttpSender(conf))
}

class InfluxDbHttpSender(conf: InfluxSenderConfig) extends InfluxDbSender with LazyLogging {
  private implicit val backend: SttpBackend[Try, Nothing, NothingT] = TryHttpURLConnectionBackend()

  private val buffer = ArrayBuffer[String]()

  logger.info(s"InfluxSender created with url: ${conf.req.uri}")

  override def connect(): Unit = {}

  override def send(measurement: lang.StringBuilder): Unit = buffer.append(measurement.toString)

  override def flush(): Unit = {
    val data = buffer.mkString
    logger.debug(s"Sending ${buffer.size} metrics for conf $conf")
    buffer.clear()
    val answer = conf.req.body(data).send()

    answer match {
      case Success(res) if res.code.isSuccess => // nothing
      case Success(res) => logger.warn(s"Failed to send data to influx: ${res.code.code}, ${res.body}")
      case Failure(ex) => logger.warn(s"Failed to send data to influx: ${ex.getMessage}", ex)
    }
  }

  override def disconnect(): Unit = {}

  override def isConnected: Boolean = true

  override def close(): Unit = {}
}


case class InfluxSenderConfig(url: String,
                              database: String,
                              `type`: String,
                              host: String,
                              environment: String,
                              prefix: Option[String],
                              retentionPolicy: Option[String],
                              username: Option[String],
                              password: Option[String],
                              reporterPolling: Duration) {


  private val params = ("db" -> database) :: username.map("u" -> _).toList ::: password.map("p" -> _).toList ::: retentionPolicy.map("rp" -> _).toList

  def req: RequestT[Identity, Either[String, String], Nothing] = basicRequest.post(uri"$url?$params")
    .contentType("application/json", StandardCharsets.UTF_8.name())

}
