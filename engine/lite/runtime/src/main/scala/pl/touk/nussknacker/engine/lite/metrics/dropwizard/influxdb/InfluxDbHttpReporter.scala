package pl.touk.nussknacker.engine.lite.metrics.dropwizard.influxdb

import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{MetricName, MetricRegistry}
import io.dropwizard.metrics5.influxdb._
import sttp.client3._

import java.lang
import java.nio.charset.StandardCharsets
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.{Failure, Success, Try}

object InfluxDbHttpReporter {

  def build(metricRegistry: MetricRegistry, prefix: MetricName, conf: InfluxSenderConfig): InfluxDbReporter =
    InfluxDbReporter
      .forRegistry(metricRegistry)
      .prefixedWith(prefix)
      .build(new InfluxDbHttpSender(conf))

}

class InfluxDbHttpSender(conf: InfluxSenderConfig) extends InfluxDbSender with LazyLogging {
  private implicit val backend: SttpBackend[Try, Any] = TryHttpURLConnectionBackend()

  private val buffer = ArrayBuffer[String]()

  logger.info(s"InfluxSender created with url: ${conf.req.uri}")

  override def connect(): Unit = {}

  override def send(measurement: lang.StringBuilder): Unit = {
    val stringValue = measurement.toString
    // TODO: this is quick solution for https://github.com/influxdata/influxdb-java/pull/616
    // In the future we'll use micrometer which should handle those better...
    if (!stringValue.contains(Double.NaN.toString)) {
      buffer.append(stringValue)
    }
  }

  override def flush(): Unit = {
    if (buffer.isEmpty) {
      // this can be useful e.g. in some test environments where we don't really have Influx
      logger.debug("No metrics to send, skipping")
    } else {
      val data = buffer.mkString
      logger.debug(s"Sending ${buffer.size} metrics for conf $conf")
      buffer.clear()
      val answer = conf.req.body(data).send(backend)

      answer match {
        case Success(res) if res.code.isSuccess => // nothing
        case Success(res) => logger.warn(s"Failed to send data to influx: ${res.code.code}, ${res.body}")
        case Failure(ex)  => logger.warn(s"Failed to send data to influx: ${ex.getMessage}", ex)
      }
    }
  }

  override def disconnect(): Unit = {}

  override def isConnected: Boolean = true

  override def close(): Unit = {}
}

case class InfluxSenderConfig(
    url: String,
    database: String,
    retentionPolicy: Option[String],
    username: Option[String],
    password: Option[String],
    reporterPolling: Duration = 10 seconds
) {

  private val params = ("db" -> database) :: username
    .map("u" -> _)
    .toList ::: password.map("p" -> _).toList ::: retentionPolicy.map("rp" -> _).toList

  // must be eager (val) because we want to validate uri during parsing
  val req: RequestT[Identity, Either[String, String], Any] = basicRequest
    .post(uri"$url?$params")
    .contentType("application/json", StandardCharsets.UTF_8.name())

  require(req.uri.isAbsolute, s"URL should be absolute, but got '$url'")
}
