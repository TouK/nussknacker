package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.serialization.universal

import org.apache.kafka.common.header.Headers

import scala.util.Try

object ConfluentUniversalKafkaSerde {
  val ValueSchemaIdHeaderName = "value.schemaId"
  val KeySchemaIdHeaderName = "key.schemaId"

  implicit class RichHeaders(h: Headers) {
    def getSchemaId(headerName: String): Option[Int] = Option(h.lastHeader(headerName))
      .map(h => new String(h.value())).map(v => Try(v.toInt).fold(e => throw new InvalidSchemaIdHeader(headerName, v, e), v => v))
  }
}

class InvalidSchemaIdHeader(headerName: String, value: String, cause: Throwable) extends IllegalArgumentException(s"Got header $headerName, but the value '$value' is invalid.", cause)
