package pl.touk.nussknacker.engine.kafka.exception

import io.circe.derivation.annotations.JsonCodec
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}

import java.io.{PrintWriter, StringWriter}
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import scala.io.Source

case class KafkaJsonExceptionSerializationSchema(metaData: MetaData, consumerConfig: KafkaExceptionConsumerConfig) {

  def serialize(exceptionInfo: EspExceptionInfo[NonTransientException]): ProducerRecord[Array[Byte], Array[Byte]] = {
    val key = s"${metaData.id}-${exceptionInfo.nodeId.getOrElse("")}".getBytes(StandardCharsets.UTF_8)
    val value = KafkaExceptionInfo(metaData, exceptionInfo, consumerConfig)
    val serializedValue = value.asJson.spaces2.getBytes(StandardCharsets.UTF_8)
    new ProducerRecord(consumerConfig.topic, key, serializedValue)
  }

}

@JsonCodec case class KafkaExceptionInfo(processName: String,
                                         nodeId: Option[String],
                                         message: Option[String],
                                         exceptionInput: Option[String],
                                         //TODO: consider using JSON here?
                                         inputEvent: Option[String],
                                         stackTrace: Option[String],
                                         timestamp: Long,
                                         host: Option[String],
                                         additionalData: Map[String, String])

object KafkaExceptionInfo {

  //TODO: better hostname (e.g. from some Flink config)
  private lazy val hostName = InetAddress.getLocalHost.getHostName

  def apply(metaData: MetaData, exceptionInfo: EspExceptionInfo[NonTransientException], config: KafkaExceptionConsumerConfig): KafkaExceptionInfo = {
    new KafkaExceptionInfo(
      metaData.id,
      exceptionInfo.nodeId,
      Option(exceptionInfo.throwable.message),
      Option(exceptionInfo.throwable.input),
      optional(exceptionInfo.context, config.includeInputEvent).map(_.toString),
      serializeStackTrace(config.stackTraceLengthLimit, exceptionInfo.throwable),
      exceptionInfo.throwable.timestamp.toEpochMilli,
      optional(hostName, config.includeHost),
      config.additionalParams
    )
  }

  private def optional[T](value: T, include: Boolean): Option[T] = Option(value).filter(_ => include)

  private def serializeStackTrace(stackTraceLengthLimit: Int, throwable: Throwable): Option[String] = {
    if (stackTraceLengthLimit == 0) {
      None
    } else {
      val writer = new StringWriter()
      throwable.printStackTrace(new PrintWriter(writer))
      Option(Source.fromString(writer.toString).getLines().take(stackTraceLengthLimit).mkString("\n"))
    }
  }

}

