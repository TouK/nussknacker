package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, MethodToInvoke, ParamName, VariableConstants}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter.Output

import java.util.UUID


//Simplistic Kafka source/sinks, assuming string as value. To be replaced with proper components
class TestComponentProvider extends ComponentProvider {

  override def providerName: String = "kafkaSources"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
      ComponentDefinition("source", KafkaSource),
      ComponentDefinition("sink", KafkaSink),
  )

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

  object KafkaSource extends SourceFactory[String] {

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("topic") topicName: String): CommonKafkaSource = new CommonKafkaSource {

      override def topics: List[String] = topicName :: Nil

      override def deserialize(context: EngineRuntimeContext, record: ConsumerRecord[Array[Byte], Array[Byte]]): Context =
        Context(UUID.randomUUID().toString).withVariable(VariableConstants.InputVariableName, new String(record.value()))
    }

  }

  object KafkaSink extends SinkFactory {
    @MethodToInvoke
    def invoke(@ParamName("topic") topicName: String, @ParamName("value") value: LazyParameter[String]): LazyParamSink[Output] =
      (evaluateLazyParameter: LazyParameterInterpreter) => {
        implicit val epi: LazyParameterInterpreter = evaluateLazyParameter
        value.map(out => new ProducerRecord[Array[Byte], Array[Byte]](topicName, out.getBytes()))
      }
  }

}


