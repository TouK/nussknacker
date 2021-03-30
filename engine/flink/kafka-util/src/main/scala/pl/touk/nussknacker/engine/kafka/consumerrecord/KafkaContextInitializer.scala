package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.flink.api.common.functions.MapFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.flink.util.context.{FlinkContextInitializer, InitContextFunction}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

import scala.reflect.ClassTag

/**
  * KafkaContextInitializer initializes Context variables with data provided in raw kafka event (see [[org.apache.kafka.clients.consumer.ConsumerRecord]]).
  * It is used when flink source function produces stream of ConsumerRecord (deserialized to proper key-value data types).
  * Produces [[pl.touk.nussknacker.engine.api.Context]] with two variables:
  * - default "input" variable which is ConsumerRecord.value
  * - metadata of kafka event, see [[pl.touk.nussknacker.engine.kafka.consumerrecord.InputMeta]]
  *
  * @tparam K - type of key of deserialized ConsumerRecord
  * @tparam V - type of value of deserialized ConsumerRecord
  */
class KafkaContextInitializer[K: ClassTag, V: ClassTag] extends FlinkContextInitializer[ConsumerRecord[K, V]] {

  import scala.collection.JavaConverters._

  override def validationContext(context: ValidationContext, name: String, result: typing.TypingResult)(implicit nodeId: NodeId): ValidationContext = {
    val validationContext = context
      .withVariable(OutputVar.customNode(name), Typed[V])
      .andThen(_.withVariable(VariableConstants.InputMetaVariableName, InputMeta.withType[K], None))
      .getOrElse(context)
    validationContext
  }

  override def initContext(processId: String, taskName: String): MapFunction[ConsumerRecord[K, V], Context] = {
    new InitContextFunction[ConsumerRecord[K, V]](processId, taskName) {
      override def map(input: ConsumerRecord[K, V]): Context = {
        val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(input.headers).asJava
        val inputMeta = InputMeta(input.key, input.topic, input.partition, input.offset, input.timestamp, headers)
        newContext
          .withVariable(VariableConstants.InputVariableName, input.value)
          .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
      }
    }
  }

}

case class InputMeta[K](key: K, topic: String, partition: Int, offset: java.lang.Long, timestamp: java.lang.Long, headers: java.util.Map[String, String])

object InputMeta {

  def withType[K: ClassTag]: typing.TypingResult = {
    val keyTypingResult = Typed[K]
    TypedObjectTypingResult(fields(keyTypingResult), objType(keyTypingResult))
  }

  def withType(keyTypingResult: typing.TypingResult): typing.TypingResult = {
    TypedObjectTypingResult(fields(keyTypingResult), objType(keyTypingResult))
  }

  private def fields(keyTypingResult: typing.TypingResult): Map[String, TypingResult] = {
    Map(
      "key" -> keyTypingResult,
      "topic" -> Typed[String],
      "partition" -> Typed[Int],
      "offset" -> Typed[java.lang.Long],
      "timestamp" -> Typed[java.lang.Long],
      "headers" -> Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[String]))
    )
  }
  private def objType[K: ClassTag](keyTypingResult: typing.TypingResult): TypedClass = {
    Typed.genericTypeClass[InputMeta[_]](List(keyTypingResult)).asInstanceOf[TypedClass]
  }
}

class KafkaAvroContextInitializer[T](keyTypingResult: typing.TypingResult) extends FlinkContextInitializer[T] {

  import scala.collection.JavaConverters._

  //fallback to String
  private val validKeyTypingResult = keyTypingResult match {
    case Unknown => Typed[String]
    case _ => keyTypingResult
  }

  override def validationContext(context: ValidationContext, name: String, result: typing.TypingResult)(implicit nodeId: NodeId): ValidationContext = {
    val validationContext = context
      .withVariable(OutputVar.customNode(name), result)
      .andThen(_.withVariable(VariableConstants.InputMetaVariableName, InputMeta.withType(validKeyTypingResult), None))
      .getOrElse(context)
    validationContext
  }

  override def initContext(processId: String, taskName: String): MapFunction[T, Context] = {
    new InitContextFunction[T](processId, taskName) {
      override def map(input: T): Context = {
        input match {
          case record: ConsumerRecord[_, _] => {
            val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(record.headers).asJava
            val inputMeta = InputMeta(record.key, record.topic, record.partition, record.offset, record.timestamp, headers)
            newContext
              .withVariable(VariableConstants.InputVariableName, record.value)
              .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
          }
          case _ =>
            throw new IllegalArgumentException(s"Avro source cannot initContext with ${input.getClass} - not a valid ConsumerRecord")
        }
      }
    }
  }
}