package pl.touk.nussknacker.engine.kafka.exception

import io.circe.{Json, JsonObject}
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
      valueJson.hcursor
        .downField("inputEvent")
        .withFocus { inputEventJson =>
          inputEventJson.asObject
            .map { inputEventJsonObject =>
              val indentLevel = 1
              removeVariablesOverSizeLimit(
                inputEventJsonObject,
                indentLevel,
                serializedValue.length,
                maxValueBytes
              ).toJson
            }
            .getOrElse(inputEventJson)
        }
        .top
        .getOrElse(valueJson)
        .spaces2
        .getBytes(StandardCharsets.UTF_8)
    } else {
      serializedValue
    }
  }

  // noinspection ScalaWeakerAccess
  def removeVariablesOverSizeLimit(
      containerObject: JsonObject,
      containerIndentLevel: Int,
      valueBytes: Int,
      maxValueBytes: Int
  ): JsonObject = {
    if (valueBytes <= maxValueBytes) {
      return containerObject
    }

    // use a placeholder of the same length as the number of digits in valueBytes,
    // because the final value will never be larger than that
    val removedBytesPlaceholder = "$" * valueBytes.toString.length
    // text below will increase JSON size, but we are working under the assumption that its size is much smaller
    // than the size of inputEvent keys that will get removed
    val messageTemplate =
      s"variables truncated, original object had $valueBytes bytes, which was more then the max allowed length of $maxValueBytes bytes. " +
        s"Removed variables ($removedBytesPlaceholder bytes)"

    val indentBytes = indentLength * (containerIndentLevel + 1)
    // |{
    // |  "inputEvent" : {
    // |    "!warning": ...
    // (note: line below uses '' as quotes because Scala 2.12 can't handle escaped "")
    val warningBytes = indentBytes + Utils.utf8Length(s"'$warningKey' : ${messageTemplate.asJson.spaces2},\n")
    val bytesToCut   = valueBytes + warningBytes - maxValueBytes

    val variablesWithLength               = countVariableLengths(containerObject, indentBytes)
    val (variablesToRemove, removedBytes) = calculateKeysToRemove(variablesWithLength, bytesToCut)

    if (removedBytes <= warningBytes) {
      // this may happen only when doing tests with really low size limits
      return containerObject
    }

    val finalMessage = messageTemplate.replace(removedBytesPlaceholder, removedBytes.toString) +
      variablesToRemove.toSeq.sorted.mkString(": ", ", ", "")

    containerObject
      .filterKeys(key => !variablesToRemove.contains(key))
      .add(warningKey, finalMessage.asJson)
  }

  private[exception] def countVariableLengths(json: JsonObject, indentBytes: Int): List[(String, Int)] = {
    // Each removed key will be moved to a message that enumerates all removed keys, e.g. when the variable
    // 'my_variable_name' is removed from:
    // |  "inputEvent" : {
    // |    "my_variable_name" : "value",
    // |  }
    // it be transformed into a partial string: `, my_variable_name` (2-byte separator + variable name).
    // Because of that the length of variable name and the last two characters (`,\n`) aren't added to counted size,
    // as the space they take up will get reused.
    //
    // Assumption that all values are terminated by `,\n` may make us overestimate a single variable by one byte,
    // but it's safer to remove more and still fit within given size limit.
    val jsonKeyOverhead = indentBytes + 2 /* key quotes */ + 3 /* ` : ` */

    json.toList
      .map { case (key, value) =>
        val serializedVariable = value.spaces2
        val variableBytes = Utils.utf8Length(serializedVariable) + (indentBytes * serializedVariable.count(_ == '\n'))
        key -> (jsonKeyOverhead + variableBytes)
      }
  }

  private def calculateKeysToRemove(keysWithLength: List[(String, Int)], bytesToRemove: Int): (Set[String], Int) = {
    @tailrec
    def collectKeysToRemove(
        allKeys: List[(String, Int)],
        keysToRemove: List[String],
        bytesSoFar: Int
    ): (Set[String], Int) = {
      allKeys match {
        case (key, bytes) :: tail if bytesSoFar < bytesToRemove =>
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
