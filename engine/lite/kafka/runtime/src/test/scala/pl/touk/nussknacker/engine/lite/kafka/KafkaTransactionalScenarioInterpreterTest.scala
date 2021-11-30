package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5.{Gauge, Histogram, Metric, MetricRegistry}
import org.scalatest.{Matchers, Outcome, fixture}
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.test.PatientScalaFutures

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.jdk.CollectionConverters.mapAsScalaMapConverter
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{Failure, Try, Using}

class KafkaTransactionalScenarioInterpreterTest extends fixture.FunSuite with KafkaSpec with Matchers with LazyLogging with PatientScalaFutures {

  private val metricRegistry = new MetricRegistry
  private val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  def withFixture(test: OneArgTest): Outcome = {
    val suffix = test.name.replaceAll("[^a-zA-Z]", "")
    val fixture = FixtureParam("input-" + suffix, "output-" + suffix, "errors-" + suffix)
    kafkaClient.createTopic(fixture.inputTopic)
    kafkaClient.createTopic(fixture.outputTopic, 1)
    kafkaClient.createTopic(fixture.errorTopic, 1)
    withFixture(test.toNoArgTest(fixture))
  }

  private def withMinTolerance(duration: Duration) = duration.toMillis +- 60000L

  test("should run scenario and pass data to output ") { fixture =>
    val inputTopic = fixture.inputTopic
    val outputTopic = fixture.outputTopic
    val errorTopic = fixture.errorTopic

    val scenario = EspProcessBuilder
      .id("test")
      .parallelism(2)
      .source("source", "source", "topic" -> s"'$inputTopic'")
      .buildSimpleVariable("throw on 0", "someVar", "1 / #input.length")
      .customNode("split", "splitVar", "split", "parts" -> "{#input, 'other'}")
      .emptySink("sink", "sink", "topic" -> s"'$outputTopic'", "value" -> "#splitVar + '-add'")

    runScenarioWithoutErrors(fixture, scenario) {
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

  test("starts without error without kafka") { fixture =>
    val scenario: EspProcess = passThroughScenario(fixture)

    val configWithFakeAddress = ConfigFactory.parseMap(Collections.singletonMap("kafka.kafkaAddress", "not_exist.pl:9092"))
    runScenarioWithoutErrors(fixture, scenario, configWithFakeAddress) {
      //TODO: figure out how to wait for starting thread pool?
      Thread.sleep(100)
    }

  }

  test("handles immediate close") { fixture =>
    val scenario: EspProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {
      //TODO: figure out how to wait for starting thread pool?
      Thread.sleep(100)
    }
  }

  test("detects fatal failure in close") { fixture =>
    val inputTopic = fixture.inputTopic
    val outputTopic = fixture.outputTopic

    val failureMessage = "EXPECTED_TO_HAPPEN"

    val scenario: EspProcess = passThroughScenario(fixture)
    val modelDataToUse = modelData(adjustConfig(fixture.errorTopic, config))
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)

    val interpreter = new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelDataToUse, preparer) {
      override private[kafka] def createScenarioTaskRun(taskId: String): Task = {
        val original = super.createScenarioTaskRun(taskId)
        //we simulate throwing exception on shutdown
        new Task {
          override def init(): Unit = original.init()

          override def run(): Unit = original.run()

          override def close(): Unit = {
            original.close()
            logger.info("Original closed, throwing expected exception")
            throw new Exception(failureMessage)
          }
        }

      }
    }
    val runResult = Using.resource(interpreter) { engine =>
      val runResult = engine.run()
      //we wait for one message to make sure everything is already running
      kafkaClient.sendMessage(inputTopic, "dummy").futureValue
      kafkaClient.createConsumer().consume(outputTopic).head
      runResult
    }
    Try(Await.result(runResult, 10 seconds)) match {
      case Failure(exception) =>
        exception.getMessage shouldBe failureMessage
      case result => throw new AssertionError(s"Should fail with completion exception, instead got: $result")
    }

  }

  test("source and kafka metrics are handled correctly") { fixture =>
    val inputTopic = fixture.inputTopic
    val outputTopic = fixture.outputTopic

    val scenario: EspProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {

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

  private def forSomeMetric[T <: Metric : ClassTag](name: String)(action: T => Assertion): Unit = {
    val results = metricsForName[T](name).map(m => Try(action(m)))
    results.exists(_.isSuccess) shouldBe true
  }

  private def metricsForName[T <: Metric : ClassTag](name: String): Iterable[T] = {
    val metrics = metricRegistry.getMetrics.asScala.collect {
      case (mName, metric: T) if mName.getKey == name => metric
    }
    metrics should not be 'empty
    metrics
  }

  private def forEachMetric[T <: Metric : ClassTag](name: String)(action: T => Any): Unit = {
    metricsForName[T](name).foreach(action)
  }

  private def runScenarioWithoutErrors[T](fixture: FixtureParam,
                                          scenario: EspProcess, config: Config = config)(action: => T): T = {
    val jobData = JobData(scenario.metaData, ProcessVersion.empty, DeploymentData.empty)
    val configToUse = adjustConfig(fixture.errorTopic, config)
    val (runResult, output) = Using.resource(new KafkaTransactionalScenarioInterpreter(scenario, jobData, modelData(configToUse), preparer)) { engine =>
      val result = engine.run()
      (result, action)
    }
    Await.result(runResult, 10 seconds)
    output
  }

  private def modelData(config: Config) = LocalModelData(config, new EmptyProcessConfigCreator)

  private def adjustConfig(errorTopic: String, config: Config) = config
    .withValue("components.kafkaSources.enabled", fromAnyRef(true))
    .withValue("kafka.\"auto.offset.reset\"", fromAnyRef("earliest"))
    .withValue("kafka.kafkaProperties.retries", fromAnyRef("1"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef(errorTopic))

  private def passThroughScenario(fixtureParam: FixtureParam) = {
    EspProcessBuilder
      .id("test")
      .source("source", "source", "topic" -> s"'${fixtureParam.inputTopic}'")
      .emptySink("sink", "sink", "topic" -> s"'${fixtureParam.outputTopic}'", "value" -> "#input")
  }

  case class FixtureParam(inputTopic: String, outputTopic: String, errorTopic: String)

}