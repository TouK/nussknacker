package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.BasicContextInitializer
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

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
class KafkaContextInitializer[K, V](outputVariableName: String, keyTypingResult: TypingResult, valueTypingResult: TypingResult)
  extends BasicContextInitializer[ConsumerRecord[K, V]](valueTypingResult, outputVariableName) {

  import scala.collection.JavaConverters._

  override def validationContext(context: ValidationContext)(implicit nodeId: NodeId): ValidationContext = {
    val contextWithInput = super.validationContext(context)
    val inputMetaTypingResult = InputMeta.withType(keyTypingResult)
    contextWithInput.withVariable(VariableConstants.InputMetaVariableName, inputMetaTypingResult, None).getOrElse(contextWithInput)
  }

  override def initContext(processId: String, nodeId: String): ConsumerRecord[K, V] => Context = input => {
    val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(input.headers).asJava
    //null won't be serialized properly
    val safeLeaderEpoch = input.leaderEpoch().orElse(-1)
    val inputMeta = InputMeta(input.key, input.topic, input.partition, input.offset, input.timestamp, input.timestampType(), headers, safeLeaderEpoch)
    super.initContext(processId, nodeId)(input)
      .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
  }

}