package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter.Output
import pl.touk.nussknacker.engine.lite.kafka.TestComponentProvider.{SourceFailure, failingInputValue}
import pl.touk.nussknacker.engine.lite.kafka.api.LiteKafkaSource

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

//Simplistic Kafka source/sinks, assuming string as value. To be replaced with proper components
class TestComponentProvider extends ComponentProvider {

  override def providerName: String = "testComponents"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    List(
      ComponentDefinition("source", KafkaSource),
      ComponentDefinition("sink", KafkaSink),
      ComponentDefinition("sleep", InitiallySleepingService),
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

  object KafkaSource extends SourceFactory {

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("topic") topicName: String)(implicit nodeIdPassed: NodeId): LiteKafkaSource = new LiteKafkaSource {

      override val nodeId: NodeId = nodeIdPassed

      override def topics: List[String] = topicName :: Nil

      override def transform(record: ConsumerRecord[Array[Byte], Array[Byte]]): Context = {
        val value = new String(record.value())
        if (value == failingInputValue)
          throw SourceFailure
        Context(contextIdGenerator.nextContextId())
          .withVariable(VariableConstants.EventTimestampVariableName, record.timestamp())
          .withVariable(VariableConstants.InputVariableName, value)
      }
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

  case object InitiallySleepingService extends Service with LazyLogging {

    private val initialized = new AtomicBoolean(false)
    @MethodToInvoke
    def invoke(): Future[Unit] = {
      Future.successful {
        if (initialized.compareAndSet(false, true)) {
          logger.info("Sleeping for 4s...")
          Thread.sleep(4000)
        } else {
          logger.debug("No need to sleep...")
        }
        ()
      }
    }
  }

}

object TestComponentProvider {

  val failingInputValue = "FAIL"

  case object SourceFailure extends Exception("Source failure")

}