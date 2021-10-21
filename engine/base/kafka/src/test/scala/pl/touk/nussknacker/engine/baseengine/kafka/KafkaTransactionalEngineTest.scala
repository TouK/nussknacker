package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalEngine.Output
import pl.touk.nussknacker.engine.baseengine.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds
import scala.util.Using

class KafkaTransactionalEngineTest extends FunSuite with KafkaSpec with Matchers {

  private val inputTopic = "input"

  private val outputTopic = "output"

  private val errorTopic = "errors"

  private lazy val adjustedConfig = config
    .withValue("auto.offset.reset", fromAnyRef("earliest"))
    .withValue("kafka.kafkaProperties.retries", fromAnyRef("1"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef(errorTopic))

  private lazy val modelData = LocalModelData(adjustedConfig, new EmptyProcessConfigCreator)

  private val preparer = new EngineRuntimeContextPreparer(NoOpMetricsProvider)

  test("should run scenario and pass data to output ") {
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(errorTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
      .id("test")
      .exceptionHandler()
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .buildSimpleVariable("throw on 0", "someVar", "1 / #input.length")
      .customNode("split", "splitVar", "split", "parts" -> "{#input, 'other'}")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#splitVar + '-add'")
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)


    Using.resource(new KafkaTransactionalEngine(scenario, jobData, modelData, preparer)) { engine =>
      val input = "original"

      kafkaClient.sendMessage(inputTopic, input).futureValue
      kafkaClient.sendMessage(inputTopic, "").futureValue

      val messages = kafkaClient.createConsumer().consume(outputTopic).take(2).map(rec => new String(rec.message()))
      messages shouldBe List("original-add", "other-add")

      val error = CirceUtil.decodeJsonUnsafe[KafkaExceptionInfo](kafkaClient
        .createConsumer().consume(errorTopic).head.message())
      error.nodeId shouldBe Some("throw on 0")
      error.processName shouldBe scenario.id
      error.exceptionInput shouldBe Some("1 / #input.length")
    }

  }

}

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

    override def clazz: Class[_] = classOf[String]

    @MethodToInvoke(returnType = classOf[String])
    def invoke(@ParamName("topic") topicName: String): CommonKafkaSource = new CommonKafkaSource {
      
      override def topics: List[String] = topicName :: Nil

      override def deserializer(context: EngineRuntimeContext, record: ConsumerRecord[Array[Byte], Array[Byte]]): Context =
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


