package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.flink.api.common.functions.MapFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
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

  override def validationContext(context: ValidationContext, name: String)(implicit nodeId: NodeId): ValidationContext = {
    context
      .withVariable(OutputVar.customNode(name), Typed[V])
      .andThen(_.withVariable(VariableConstants.InputMetaVariableName, Typed[InputMeta[K]], None))
      .getOrElse(context)
  }

  override def initContext(processId: String, taskName: String): MapFunction[ConsumerRecord[K, V], Context] = {
    new InitContextFunction[ConsumerRecord[K, V]](processId, taskName) {
      override def map(input: ConsumerRecord[K, V]): Context = {
        val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(input.headers).mapValues(_.orNull).asJava
        val inputMeta = InputMeta(input.key, input.topic, input.partition, input.offset, input.timestamp, headers)
        newContext
          .withVariable(VariableConstants.InputVariableName, input.value)
          .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
      }
    }
  }

}

case class InputMeta[K](key: K, topic: String, partition: Int, offset: java.lang.Long, timestamp: java.lang.Long, headers: java.util.Map[String, String])
