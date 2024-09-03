package pl.touk.nussknacker.engine.kafka.exception

import io.circe.Json
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.io.{PrintWriter, StringWriter}
import java.lang
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import scala.io.Source

class KafkaJsonExceptionSerializationSchema(metaData: MetaData, consumerConfig: KafkaExceptionConsumerConfig)
    extends KafkaSerializationSchema[NuExceptionInfo[NonTransientException]] {

  override def serialize(
      exceptionInfo: NuExceptionInfo[NonTransientException],
      timestamp: lang.Long
  ): ProducerRecord[Array[Byte], Array[Byte]] = {
    val key =
      s"${metaData.name}-${exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse("")}"
        .getBytes(StandardCharsets.UTF_8)
    val value           = KafkaExceptionInfo(metaData, exceptionInfo, consumerConfig)
    val serializedValue = value.asJson.spaces2.getBytes(StandardCharsets.UTF_8)
    new ProducerRecord(consumerConfig.topic, key, serializedValue)
  }

}

@JsonCodec case class KafkaExceptionInfo(
    processName: ProcessName,
    nodeId: Option[String],
    message: Option[String],
    exceptionInput: Option[String],
    inputEvent: Option[Json],
    stackTrace: Option[String],
    timestamp: Long,
    host: Option[String],
    additionalData: Map[String, String]
)

object KafkaExceptionInfo {

  private val encoder = ToJsonEncoder(failOnUnknown = false, getClass.getClassLoader)

  // TODO: better hostname (e.g. from some Flink config)
  private lazy val hostName = InetAddress.getLocalHost.getHostName

  def apply(
      metaData: MetaData,
      exceptionInfo: NuExceptionInfo[NonTransientException],
      config: KafkaExceptionConsumerConfig
  ): KafkaExceptionInfo = {
    new KafkaExceptionInfo(
      metaData.name,
      exceptionInfo.nodeComponentInfo.map(_.nodeId),
      Option(exceptionInfo.throwable.message),
      Option(exceptionInfo.throwable.input),
      optional(exceptionInfo.context.allVariables, config.includeInputEvent).map(encoder.encode),
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
