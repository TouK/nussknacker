package pl.touk.nussknacker.engine.lite.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{ProcessName, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.Output
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource

//Simplistic Kafka source/sinks, assuming string as value. To be replaced with proper components
object TestComponentProvider {

  final val TopicParamName     = "Topic"
  final val SinkValueParamName = "Value"

  final val FailingInputValue = "FAIL"

  case object SourceFailure extends Exception("Source failure")

  val Components: List[ComponentDefinition] = List(
    ComponentDefinition("source", KafkaSource),
    ComponentDefinition("sink", KafkaSink),
  )

  object KafkaSource extends SourceFactory {

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName(`TopicParamName`) topicName: String)(implicit nodeIdPassed: NodeId): LiteKafkaSource =
      new LiteKafkaSource {

        override val nodeId: NodeId = nodeIdPassed

        override def topics: List[String] = topicName :: Nil

        override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
          val value = new String(record.value())
          if (value == FailingInputValue)
            throw SourceFailure
          Context(contextIdGenerator.nextContextId())
            .withVariable(VariableConstants.EventTimestampVariableName, record.timestamp())
            .withVariable(VariableConstants.InputVariableName, value)
        }

        // TODO local: impossible without adding dependencies?
        override def consumerGroupDeterminer: (ProcessName, NodeId) => String = (_, _) => "stubConsumerGroupId"

      }

  }

  object KafkaSink extends SinkFactory {

    @MethodToInvoke
    def invoke(
        @ParamName(`TopicParamName`) topicName: String,
        @ParamName(SinkValueParamName) value: LazyParameter[String]
    ): LazyParamSink[Output] =
      (evaluateLazyParameter: LazyParameterInterpreter) => {
        implicit val epi: LazyParameterInterpreter = evaluateLazyParameter
        value.map(out => new ProducerRecord[Array[Byte], Array[Byte]](topicName, out.getBytes()))
      }

  }

}
