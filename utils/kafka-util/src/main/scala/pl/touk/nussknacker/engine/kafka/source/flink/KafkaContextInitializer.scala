package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.VariableConstants
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.process.BasicGenericContextInitializer
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

// TODO move to non-flink package
/**
  * KafkaContextInitializer initializes Context variables with data provided in raw kafka event (see [[org.apache.kafka.clients.consumer.ConsumerRecord]]).
  * It is used when flink source function produces stream of ConsumerRecord (deserialized to proper key-value data types).
  * Produces [[pl.touk.nussknacker.engine.api.Context]] with two variables:
  * - default "input" variable which is set up with ConsumerRecord.value
  * - metadata of kafka event, see [[InputMeta]]
  *
  * @param keyTypingResult - provides information  on key type to specify metadata variable
  * @param valueTypingResult - provides information  on value type to specify main "#input" variable
  * @tparam K - type of key of deserialized ConsumerRecord
  * @tparam V - type of value of deserialized ConsumerRecord
  */
abstract class KafkaContextInitializer[K, V, DefinedParameter <: BaseDefinedParameter](keyTypingResult: TypingResult, valueTypingResult: TypingResult)
  extends BasicGenericContextInitializer[ConsumerRecord[K, V], DefinedParameter] {

  override def validationContext(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)])
                                (implicit nodeId: NodeId): ValidationContext = {
    val contextWithInput = super.validationContext(context, dependencies, parameters)
    val inputMetaTypingResult = InputMeta.withType(keyTypingResult)
    contextWithInput.withVariable(VariableConstants.InputMetaVariableName, inputMetaTypingResult, None).getOrElse(contextWithInput)
  }

  override protected def outputVariableType(context: ValidationContext, dependencies: List[NodeDependencyValue], parameters: List[(String, DefinedParameter)])
                                           (implicit nodeId: NodeId): TypingResult = valueTypingResult

}