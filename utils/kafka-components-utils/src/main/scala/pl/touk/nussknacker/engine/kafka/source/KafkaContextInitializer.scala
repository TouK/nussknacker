package pl.touk.nussknacker.engine.kafka.source

import cats.data.ValidatedNel
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{
  BasicContextInitializer,
  BasicContextInitializingFunction,
  ContextInitializingFunction
}
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.kafka.KafkaRecordUtils

import java.util

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
class KafkaContextInitializer[K, V](
    outputVariableName: String,
    keyTypingResult: TypingResult,
    valueTypingResult: TypingResult
) extends BasicContextInitializer[ConsumerRecord[K, V]](valueTypingResult, outputVariableName) {

  import scala.jdk.CollectionConverters._

  override def validationContext(
      context: ValidationContext
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val contextWithInput      = super.validationContext(context)
    val inputMetaTypingResult = InputMeta.withType(keyTypingResult)
    contextWithInput.andThen(_.withVariable(VariableConstants.InputMetaVariableName, inputMetaTypingResult, None))
  }

  override def initContext(contextIdGenerator: ContextIdGenerator): ContextInitializingFunction[ConsumerRecord[K, V]] =
    new BasicContextInitializingFunction[ConsumerRecord[K, V]](contextIdGenerator, outputVariableName) {

      override def apply(input: ConsumerRecord[K, V]): Context = {
        // Scala map wrapper causes some serialization problems
        val headers: util.Map[String, String] = new util.HashMap(KafkaRecordUtils.toMap(input.headers).asJava)
        // null won't be serialized properly
        val safeLeaderEpoch = input.leaderEpoch().orElse(-1)
        val inputMeta = InputMeta(
          input.key,
          input.topic,
          input.partition,
          input.offset,
          input.timestamp,
          input.timestampType(),
          headers,
          safeLeaderEpoch
        )
        newContext
          .withVariable(VariableConstants.InputVariableName, input.value())
          .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
      }

    }

}
