package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.functions.MapFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.flink.api.process.{BasicContextInitializingFunction, BasicFlinkGenericContextInitializer}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

/**
  * KafkaContextInitializer initializes Context variables with data provided in raw kafka event (see [[org.apache.kafka.clients.consumer.ConsumerRecord]]).
  * It is used when flink source function produces stream of ConsumerRecord (deserialized to proper key-value data types).
  * Produces [[pl.touk.nussknacker.engine.api.Context]] with two variables:
  * - default "input" variable which is set up with ConsumerRecord.value
  * - metadata of kafka event, see [[pl.touk.nussknacker.engine.kafka.source.InputMeta]]
  *
  * @param keyTypingResult - provides information  on key type to specify metadata variable
  * @param valueTypingResult - provides information  on value type to specify main "#input" variable
  * @tparam K - type of key of deserialized ConsumerRecord
  * @tparam V - type of value of deserialized ConsumerRecord
  */
class KafkaContextInitializer[K, V, DefinedParameter <: BaseDefinedParameter](keyTypingResult: TypingResult, valueTypingResult: TypingResult)
  extends BasicFlinkGenericContextInitializer[ConsumerRecord[K, V], DefinedParameter] {

  import scala.collection.JavaConverters._

  override def validationContext(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)])
                                (implicit nodeId: NodeId): ValidationContext = {
    val contextWithInput = super.validationContext(context, dependencies, parameters)
    val inputMetaTypingResult = InputMeta.withType(keyTypingResult)
    contextWithInput.withVariable(VariableConstants.InputMetaVariableName, inputMetaTypingResult, None).getOrElse(contextWithInput)
  }

  override protected def outputVariableType(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)])
                                           (implicit nodeId: NodeId): TypingResult = valueTypingResult

  override def initContext(processId: String, taskName: String): MapFunction[ConsumerRecord[K, V], Context] = {
    new BasicContextInitializingFunction[ConsumerRecord[K, V]](processId, taskName) {
      override def map(input: ConsumerRecord[K, V]): Context = {
        val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(input.headers).asJava
        val inputMeta = InputMeta(input.key, input.topic, input.partition, input.offset, input.timestamp, input.timestampType(), headers, input.leaderEpoch().orElse(null))
        newContext
          .withVariable(VariableConstants.InputVariableName, input.value)
          .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
      }
    }
  }

}
