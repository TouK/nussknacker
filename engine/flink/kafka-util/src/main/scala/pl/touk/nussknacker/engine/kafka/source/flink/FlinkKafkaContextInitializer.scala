package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.flink.api.common.functions.MapFunction
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkContextInitializingFunction, FlinkContextInitializer}
import pl.touk.nussknacker.engine.kafka.ConsumerRecordUtils

trait FlinkKafkaContextInitializer[K, V] extends FlinkContextInitializer[ConsumerRecord[K, V]] {

  import scala.collection.JavaConverters._

  override def initContext(processId: String, nodeId: String): MapFunction[ConsumerRecord[K, V], Context] = {
    new BasicFlinkContextInitializingFunction[ConsumerRecord[K, V]](processId, nodeId) {
      override def map(input: ConsumerRecord[K, V]): Context = {
        val headers: java.util.Map[String, String] = ConsumerRecordUtils.toMap(input.headers).asJava
        //null won't be serialized properly
        val safeLeaderEpoch = input.leaderEpoch().orElse(-1)
        val inputMeta = InputMeta(input.key, input.topic, input.partition, input.offset, input.timestamp, input.timestampType(), headers, safeLeaderEpoch)
        newContext
          .withVariable(VariableConstants.InputVariableName, input.value)
          .withVariable(VariableConstants.InputMetaVariableName, inputMeta)
      }
    }
  }

}