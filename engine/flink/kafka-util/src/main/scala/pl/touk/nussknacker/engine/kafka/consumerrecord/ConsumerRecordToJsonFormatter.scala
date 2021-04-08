package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.nio.charset.{Charset, StandardCharsets}

import com.github.ghik.silencer.silent
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestDataSplit, TestParsingUtils}
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, RecordFormatter}
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.annotation.nowarn

@silent("deprecated")
@nowarn("cat=deprecation")
class ConsumerRecordToJsonFormatter extends RecordFormatter {

  private val cs: Charset = StandardCharsets.UTF_8

  // TODO: add better key-value encoding and decoding
  implicit val consumerRecordEncoder: Encoder[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    val encode: Encoder[Any] = BestEffortJsonEncoder(failOnUnkown = false).circeEncoder
    new Encoder[ConsumerRecord[Array[Byte], Array[Byte]]] {
      override def apply(a: ConsumerRecord[Array[Byte], Array[Byte]]): Json = Json.obj(
        "topic" -> encode(a.topic()),
        "partition" -> encode(a.partition()),
        "offset" -> encode(a.offset()),
        "timestamp" -> encode(a.timestamp()),
        "timestampType" -> encode(a.timestampType().name),
        "serializedKeySize" -> encode(a.serializedKeySize()),
        "serializedValueSize" -> encode(a.serializedValueSize()),
        "key" -> encode(Option(a.key()).map(bytes => new String(bytes)).orNull),
        "value" -> encode(new String(a.value())),
        "leaderEpoch" -> encode(a.leaderEpoch().orElse(null)),
        "checksum" -> encode(a.checksum()),
        "headers" -> encode(ConsumerRecordUtils.toMap(a.headers()))
      )
    }
  }

  implicit val consumerRecordDecoder: Decoder[ConsumerRecord[Array[Byte], Array[Byte]]] = {
    new Decoder[ConsumerRecord[Array[Byte], Array[Byte]]] {
      override def apply(c: HCursor): Result[ConsumerRecord[Array[Byte], Array[Byte]]] = {
        for {
          topic <- c.downField("topic").as[String].right
          partition <- c.downField("partition").as[Int].right
          offset <- c.downField("offset").as[Long].right
          timestamp <- c.downField("timestamp").as[Option[Long]].right
          timestampType <- c.downField("timestampType").as[Option[String]].right
          serializedKeySize <- c.downField("serializedKeySize").as[Option[Int]].right
          serializedValueSize <- c.downField("serializedValueSize").as[Option[Int]].right
          key <- c.downField("key").as[Option[String]].right
          value <- c.downField("value").as[Option[String]].right
          leaderEpoch <- c.downField("leaderEpoch").as[Option[Int]].right
          checksum <- c.downField("checksum").as[Option[java.lang.Long]].right
          headers <- c.downField("headers").as[Map[String, Option[String]]].right
        } yield new ConsumerRecord[Array[Byte], Array[Byte]](
          topic,
          partition,
          offset,
          timestamp.getOrElse(ConsumerRecord.NO_TIMESTAMP),
          timestampType.map(TimestampType.forName).getOrElse(TimestampType.NO_TIMESTAMP_TYPE),
          checksum.getOrElse(ConsumerRecord.NULL_CHECKSUM.toLong),
          serializedKeySize.getOrElse(ConsumerRecord.NULL_SIZE),
          serializedValueSize.getOrElse(ConsumerRecord.NULL_SIZE),
          key.map(_.getBytes()).orNull,
          value.map(_.getBytes()).orNull,
          ConsumerRecordUtils.toHeaders(headers.mapValues(v => v.orNull)),
          java.util.Optional.ofNullable(leaderEpoch.map(Integer.valueOf).orNull)
        )
      }
    }
  }

  override protected def formatRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): Array[Byte] = {
    implicitly[Encoder[ConsumerRecord[Array[Byte], Array[Byte]]]].apply(record).noSpaces.getBytes(cs)
  }

  override protected def parseRecord(topic: String, bytes: Array[Byte]): ConsumerRecord[Array[Byte], Array[Byte]] = {
    CirceUtil.decodeJsonUnsafe[ConsumerRecord[Array[Byte], Array[Byte]]](bytes)
  }

  override protected def testDataSplit: TestDataSplit = TestParsingUtils.newLineSplit

}
