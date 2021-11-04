package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextPreparer}
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter.Output
import pl.touk.nussknacker.engine.baseengine.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures

import java.lang.Thread.UncaughtExceptionHandler
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters.asScalaBufferConverter
import scala.language.higherKinds
import scala.util.Using

class KafkaTransactionalScenarioInterpreterTest extends FunSuite with KafkaSpec with Matchers with LazyLogging with PatientScalaFutures {

  private val preparer = new EngineRuntimeContextPreparer(NoOpMetricsProvider)

  test("should run scenario and pass data to output ") {
    val inputTopic = s"input-1"
    val outputTopic = s"output-1"
    val errorTopic = s"errors-1"
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

    val (_, uncaughtErrors) = handlingFatalErrors { commonHandler =>
      Using.resource(new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData(errorTopic), preparer, commonHandler)) { engine =>
        engine.run()

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
    uncaughtErrors shouldBe 'empty
  }

  test("detects fatal failure in close") {
    val inputTopic = s"input-fatal"
    val outputTopic = s"output-fatal"
    val errorTopic = s"errors-fatal"
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(errorTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val failureMessage = "EXPECTED_TO_HAPPEN"

    val scenario = EspProcessBuilder
      .id("test")
      .exceptionHandler()
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    val (_, uncaughtErrors) = handlingFatalErrors { commonHandler =>

      val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData(errorTopic), preparer, commonHandler) {
        override private[kafka] def createScenarioTaskRun(): Runnable with AutoCloseable = {
          val original = super.createScenarioTaskRun()
          //we simulate throwing exception on shutdown
          new Runnable with AutoCloseable {
            override def run(): Unit = original.run()
            override def close(): Unit = {
              logger.info("Throwing expected exception")
              throw new Exception(failureMessage)
            }
          }

        }
      }
      Using.resource(interpreter) { engine =>
        engine.run()
        //we wait for one message to make sure everything is already running
        kafkaClient.sendMessage(inputTopic, "dummy").futureValue
        kafkaClient.createConsumer().consume(outputTopic).head
      }
    }
    uncaughtErrors.map(_.getMessage) shouldBe List(failureMessage)
  }

  //In production sth like UncaughtExceptionHandlers.systemExit() should be used, but we don't want to break CI :)
  def handlingFatalErrors[T](action: UncaughtExceptionHandler => T): (T, List[Throwable]) = {
    val uncaughtErrors = new CopyOnWriteArrayList[Throwable]()
    val commonHandler = new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.info(s"Unexpected error added in ${Thread.currentThread()} from thread $t")
        uncaughtErrors.add(e)
      }
    }
    val out = action(commonHandler)
    logger.info(s"Uncaught errors: ${uncaughtErrors.asScala.toList}")
    (out, uncaughtErrors.asScala.toList)
  }

  private def modelData(errorTopic: String) = LocalModelData(adjustedConfig(errorTopic), new EmptyProcessConfigCreator)

  private def adjustedConfig(errorTopic: String) = config
    .withValue("auto.offset.reset", fromAnyRef("earliest"))
    .withValue("kafka.kafkaProperties.retries", fromAnyRef("1"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef(errorTopic))

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


