package pl.touk.nussknacker.engine.baseengine.kafka

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{Gauge, Histogram, Metric, MetricRegistry}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{Assertion, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, SourceFactory}
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.EngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.baseengine.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.baseengine.kafka.KafkaTransactionalScenarioInterpreter.Output
import pl.touk.nussknacker.engine.baseengine.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures

import java.lang.Thread.UncaughtExceptionHandler
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.jdk.CollectionConverters.{asScalaBufferConverter, mapAsScalaMapConverter}
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{Try, Using}

class KafkaTransactionalScenarioInterpreterTest extends FunSuite with KafkaSpec with Matchers with LazyLogging with PatientScalaFutures {

  private val metricRegistry = new MetricRegistry

  private val preparer = new EngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  test("should run scenario and pass data to output ") {
    val inputTopic = s"input-1"
    val outputTopic = s"output-1"
    val errorTopic = s"errors-1"
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(errorTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
      .id("test")
      .parallelism(2)
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
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    val (_, uncaughtErrors) = handlingFatalErrors { commonHandler =>

      val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData(errorTopic), preparer, commonHandler) {
        override private[kafka] def createScenarioTaskRun(taskId: String): Runnable with AutoCloseable = {
          val original = super.createScenarioTaskRun(taskId)
          //we simulate throwing exception on shutdown
          new Runnable with AutoCloseable {
            override def run(): Unit = original.run()
            override def close(): Unit = {
              original.close()
              logger.info("Original closed, throwing expected exception")
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
    //VM may call uncaughtExceptionHandler after ExecutorService.awaitTermination completes, so we may have to wait
    eventually {
      uncaughtErrors.asScala.toList.map(_.getMessage) shouldBe List(failureMessage)
    }
  }

  test("source and kafka metrics are handled correctly") {
    val inputTopic = s"input-metrics"
    val outputTopic = s"output-metrics"
    val errorTopic = s"errors-metrics"
    kafkaClient.createTopic(inputTopic)
    kafkaClient.createTopic(errorTopic)
    kafkaClient.createTopic(outputTopic, 1)

    val scenario = EspProcessBuilder
      .id("test")
      .parallelism(2)
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#input")
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)


    val (_, uncaughtErrors) = handlingFatalErrors { commonHandler =>
      Using.resource(new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData(errorTopic), preparer, commonHandler)) { engine =>
        engine.run()

        val timestamp = Instant.now().minus(10, ChronoUnit.HOURS)

        val input = "original"

        kafkaClient.sendRawMessage(inputTopic, Array(), input.getBytes(), None, timestamp.toEpochMilli).futureValue
        kafkaClient.createConsumer().consume(outputTopic).head

        forSomeMetric[Gauge[Long]]("eventtimedelay.minimalDelay")(_.getValue shouldBe withMinTolerance(10 hours))
        forSomeMetric[Histogram]("eventtimedelay.histogram")(_.getSnapshot.getMin shouldBe withMinTolerance(10 hours))
        forSomeMetric[Histogram]("eventtimedelay.histogram")(_.getCount shouldBe 1)

        forSomeMetric[Gauge[Long]]("records-lag-max")(_.getValue shouldBe 0)
        forEachMetric[Gauge[Double]]("outgoing-byte-total")(_.getValue should be > 0.0)

      }
    }
    uncaughtErrors shouldBe 'empty

  }

  private def withMinTolerance(duration: Duration) = duration.toMillis +- 60000L

  private def forSomeMetric[T<:Metric:ClassTag](name: String)(action: T => Assertion): Unit = {
    val results = metricsForName[T](name).map(m => Try(action(m)))
    results.exists(_.isSuccess) shouldBe true
  }

  private def forEachMetric[T<:Metric:ClassTag](name: String)(action: T => Any): Unit = {
    metricsForName[T](name).foreach(action)
  }

  private def metricsForName[T<:Metric:ClassTag](name: String): Iterable[T] = {
    val metrics = metricRegistry.getMetrics.asScala.collect {
      case (mName, metric: T) if mName.getKey == name => metric
    }
    metrics should not be 'empty
    metrics
  }


  //In production sth like UncaughtExceptionHandlers.systemExit() should be used, but we don't want to break CI :)
  def handlingFatalErrors[T](action: UncaughtExceptionHandler => T): (T, java.util.List[Throwable]) = {
    val uncaughtErrors = new CopyOnWriteArrayList[Throwable]()
    val commonHandler = new UncaughtExceptionHandler {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        logger.info(s"Unexpected error added in ${Thread.currentThread()} from thread $t")
        uncaughtErrors.add(e)
      }
    }
    val out = action(commonHandler)
    (out, uncaughtErrors)
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


