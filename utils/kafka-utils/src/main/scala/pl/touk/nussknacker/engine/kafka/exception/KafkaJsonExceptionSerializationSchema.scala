package pl.touk.nussknacker.engine.kafka.exception

import io.circe.{ACursor, Json}
import io.circe.generic.JsonCodec
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.record.DefaultRecordBatch
import org.apache.kafka.common.utils.Utils
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchema
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import java.io.{PrintWriter, StringWriter}
import java.lang
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.io.Source

object KafkaJsonExceptionSerializationSchema {
  private[exception] val recordOverhead = DefaultRecordBatch.RECORD_BATCH_OVERHEAD
  private val indentLength              = 2
  private val warningKey                = "!warning"

  private def serializeValueWithSizeLimit(value: KafkaExceptionInfo, maxValueBytes: Int): Array[Byte] = {
    val valueJson       = value.asJson
    val serializedValue = valueJson.spaces2.getBytes(StandardCharsets.UTF_8)
    if (value.inputEvent.isDefined && serializedValue.length > maxValueBytes) {
      removeInputEventKeys(valueJson, serializedValue.length, maxValueBytes).spaces2.getBytes(StandardCharsets.UTF_8)
    } else {
      serializedValue
    }
  }

  private def removeInputEventKeys(value: Json, valueBytes: Int, maxValueBytes: Int): Json = {
    if (valueBytes <= maxValueBytes) {
      return value
    }

    val removedBytesPlaceholder = "$" * valueBytes.toString.length
    // text below will increase JSON size, but we are working under the assumption that its size is much smaller
    // than the size of inputEvent keys that will get removed
    val messageTemplate =
      s"inputEvent truncated, original error event had $valueBytes bytes, which was more then the max allowed length of $maxValueBytes bytes. " +
        s"Removed variables ($removedBytesPlaceholder bytes)"

    // |{
    // |  "inputEvent" : {
    // |    "name": ...
    // (note: line below uses '' as quotes because Scala 2.12 can't handle escaped "")
    val warningBytes = 2 * indentLength + Utils.utf8Length(s"'$warningKey' : ${messageTemplate.asJson.spaces2},\n")
    val bytesToCut   = valueBytes + warningBytes - maxValueBytes

    val variablesWithLength               = countVariableLengths(value.hcursor.downField("inputEvent"))
    val (variablesToRemove, removedBytes) = calculateKeysToRemove(variablesWithLength, bytesToCut)

    if (removedBytes <= warningBytes) {
      // this may happen only when doing tests with really low size limits
      return value
    }

    val finalMessage = messageTemplate.replace(removedBytesPlaceholder, removedBytes.toString) +
      variablesToRemove.toSeq.sorted.mkString(": ", ", ", "")

    value.hcursor
      .downField("inputEvent")
      .withFocus { json =>
        json.asObject
          .map { inputEventObject =>
            inputEventObject
              .filterKeys(key => !variablesToRemove.contains(key))
              .add(warningKey, finalMessage.asJson)
              .toJson
          }
          .getOrElse(json)
      }
      .top
      .getOrElse(value)
  }

  private[exception] def countVariableLengths(inputEvent: ACursor): List[(String, Int)] = {
    // Each removed key will be moved to a message that enumerates all removed keys, so this:
    // |  "inputEvent" : {
    // |    "variable_name" : "value",
    // |  }
    // will be transformed into a partial string: `, variable_name` (2-byte separator + variable name).
    // Because of that counted size doesn't include `,\n`, as these bytes will be reused.
    //
    // Assumption that all values are terminated by `,\n` may make us overestimate a single variable by one byte,
    // but it's safer to remove more and still fit within given size limit.
    val jsonIndent      = 2 * indentLength
    val jsonKeyOverhead = jsonIndent + 2 /* key quotes */ + 3 /* ` : ` */

    inputEvent.keys
      .getOrElse(Iterable.empty)
      .map { key =>
        val variableBytes = inputEvent
          .get[Json](key)
          .map { variable =>
            val serializedVariable = variable.spaces2
            Utils.utf8Length(serializedVariable) + (jsonIndent * serializedVariable.count(_ == '\n'))
          }
          .getOrElse(0)
        key -> (jsonKeyOverhead + variableBytes)
      }
      .toList
  }

  private def calculateKeysToRemove(keysWithLength: List[(String, Int)], bytesToRecover: Int): (Set[String], Int) = {
    @tailrec
    def collectKeysToRemove(
        allKeys: List[(String, Int)],
        keysToRemove: List[String],
        bytesSoFar: Int
    ): (Set[String], Int) = {
      allKeys match {
        case (key, bytes) :: tail if bytesSoFar < bytesToRecover =>
          collectKeysToRemove(tail, key :: keysToRemove, bytes + bytesSoFar)
        case _ =>
          (keysToRemove.toSet, bytesSoFar)
      }
    }

    collectKeysToRemove(keysWithLength.sortBy { case (_, length) => length }.reverse, List.empty, 0)
  }

}

class KafkaJsonExceptionSerializationSchema(metaData: MetaData, consumerConfig: KafkaExceptionConsumerConfig)
    extends KafkaSerializationSchema[NuExceptionInfo[NonTransientException]] {

  import KafkaJsonExceptionSerializationSchema._

  override def serialize(
      exceptionInfo: NuExceptionInfo[NonTransientException],
      timestamp: lang.Long
  ): ProducerRecord[Array[Byte], Array[Byte]] = {
    val key =
      s"${metaData.name}-${exceptionInfo.nodeComponentInfo.map(_.nodeId).getOrElse("")}"
        .getBytes(StandardCharsets.UTF_8)
    val value           = KafkaExceptionInfo(metaData, exceptionInfo, consumerConfig)
    val maxValueBytes   = consumerConfig.maxMessageBytes - key.length - recordOverhead
    val serializedValue = serializeValueWithSizeLimit(value, maxValueBytes)
    new ProducerRecord(consumerConfig.topic, null, timestamp, key, serializedValue)
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
