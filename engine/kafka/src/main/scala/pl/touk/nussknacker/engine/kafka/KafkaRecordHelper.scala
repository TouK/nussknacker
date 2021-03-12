package pl.touk.nussknacker.engine.kafka

import java.nio.charset.{Charset, StandardCharsets}

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders

import scala.collection.JavaConverters._

object KafkaRecordHelper {

  private val cs: Charset = StandardCharsets.UTF_8

  def emptyHeaders: RecordHeaders = new RecordHeaders()

  def toHeaders(map: Map[String, Option[String]]): RecordHeaders = {
    val headers = new RecordHeaders()
    map.foreach { case (key, value) =>
      headers.add(key, value.map(_.getBytes(cs)).orNull)
    }
    headers
  }

  def toMap(headers: Headers): Map[String, Option[String]] = {
    headers.asScala
      .map(h => (h.key(), Option(h.value()).map(new String(_, cs))))
      .toMap
  }

}
