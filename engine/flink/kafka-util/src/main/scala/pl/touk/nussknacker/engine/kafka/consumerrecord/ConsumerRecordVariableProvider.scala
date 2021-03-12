package pl.touk.nussknacker.engine.kafka.consumerrecord

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Context, ContextInterpreter}
import pl.touk.nussknacker.engine.flink.util.context.{InitContextFunction, VariableProvider}

import scala.reflect.ClassTag

class ConsumerRecordVariableProvider[K: ClassTag, V: ClassTag] extends VariableProvider[DeserializedConsumerRecord[K,V]] {

  import ConsumerRecordVariableProvider.InputMetaVariableName

  import scala.collection.JavaConverters._

  override def validationContext(context: ValidationContext, name: String)(implicit nodeId: NodeId): ValidationContext = {
    context
      .withVariable(OutputVar.customNode(name), Typed[V])
      .andThen(_.withVariable(InputMetaVariableName, Typed[InputMeta[K]], None))
      .getOrElse(context)
  }

  override def initContext(processId: String, taskName: String): InitContextFunction[DeserializedConsumerRecord[K,V]] = {
    new InitContextFunction[DeserializedConsumerRecord[K,V]](processId, taskName) {
      override def map(input: DeserializedConsumerRecord[K,V]): Context = {
        val headers: java.util.Map[String, String] = input.headers.mapValues(_.orNull).asJava
        val inputMeta = InputMeta(input.key.get, input.topic, input.partition, input.offset, input.timestamp, headers)
        newContext
          .withVariable(ContextInterpreter.InputVariableName, input.value)
          .withVariable(InputMetaVariableName, inputMeta)
      }
    }
  }

}

object ConsumerRecordVariableProvider {
  val InputMetaVariableName: String = "inputMeta"
}

case class InputMeta[K](key: K, topic: String, partition: Int, offset: java.lang.Long, timestamp: java.lang.Long, headers: java.util.Map[String, String])
