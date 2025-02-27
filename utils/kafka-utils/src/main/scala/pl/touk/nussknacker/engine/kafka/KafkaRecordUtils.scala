package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders

import java.nio.charset.{Charset, StandardCharsets}
import scala.jdk.CollectionConverters._

object KafkaRecordUtils {

  private val cs: Charset = StandardCharsets.UTF_8

  def emptyHeaders: RecordHeaders = new RecordHeaders()

  def toHeaders(list: (String, String)*): Headers = {
    toHeaders(list.toMap)
  }

  def toHeaders(map: Map[String, String]): Headers = {
    val headers = new RecordHeaders()
    map.foreach { case (key, value) =>
      headers.add(key, Option(value).map(_.getBytes(cs)).orNull)
    }
    headers
  }

  def toMap(headers: Headers): Map[String, String] = {
    headers.asScala
      .map(header => (header.key(), Option(header.value()).map(new String(_, cs)).orNull))
      .toMap
  }

}
