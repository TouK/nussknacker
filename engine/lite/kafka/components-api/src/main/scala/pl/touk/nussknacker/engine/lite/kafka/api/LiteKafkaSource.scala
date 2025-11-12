package pl.touk.nussknacker.engine.lite.kafka.api

import cats.data.NonEmptyList
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.TraceId
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._
import scala.language.higherKinds

trait LiteKafkaSource extends BaseLiteSource[ConsumerRecord[Array[Byte], Array[Byte]]] {

  def topics: NonEmptyList[TopicName.ForSource]

  override protected def extractTraceId(record: ConsumerRecord[Array[Byte], Array[Byte]]): Option[TraceId] =
    record
      .headers()
      .asScala
      .map { header =>
        header.key() -> new String(header.value(), StandardCharsets.UTF_8)
      }
      .toMap
      .get(BaseLiteSource.DefaultTraceIdHeader)
      .map(TraceId(_))

}
