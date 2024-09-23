package pl.touk.nussknacker.engine.lite.kafka

import com.typesafe.config.ConfigValueFactory.fromAnyRef
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.dropwizard.metrics5._
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, OptionValues, Outcome}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.exception.KafkaExceptionInfo
import pl.touk.nussknacker.engine.lite.ScenarioInterpreterFactory
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.components.LiteBaseComponentProvider
import pl.touk.nussknacker.engine.lite.kafka.TestComponentProvider.{SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.lite.metrics.dropwizard.DropwizardMetricsProviderFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{KafkaConfigProperties, PatientScalaFutures}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.util.{Failure, Try, Using}

class KafkaTransactionalScenarioInterpreterTest
    extends FixtureAnyFunSuite
    with KafkaSpec
    with Matchers
    with LazyLogging
    with PatientScalaFutures
    with OptionValues {

  import KafkaTransactionalScenarioInterpreter._
  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer

  private val metricRegistry = new MetricRegistry
  private val preparer = new LiteEngineRuntimeContextPreparer(new DropwizardMetricsProviderFactory(metricRegistry))

  def withFixture(test: OneArgTest): Outcome = {
    val suffix  = test.name.replaceAll("[^a-zA-Z]", "")
    val fixture = FixtureParam("input-" + suffix, "output-" + suffix, "errors-" + suffix)
    kafkaClient.createTopic(fixture.inputTopic)
    kafkaClient.createTopic(fixture.outputTopic, 1)
    kafkaClient.createTopic(fixture.errorTopic, 1)
    withFixture(test.toNoArgTest(fixture))
  }

  private def withMinTolerance(duration: Duration) = duration.toMillis +- 60000L

  test("union, passing timestamp source to sink, dual source") { fixture =>
    val inputTopic     = fixture.inputTopic
    val outputTopic    = fixture.outputTopic
    val inputTimestamp = System.currentTimeMillis()
    val scenario = ScenarioBuilder
      .streamingLite("sample-union")
      .sources(
        GraphBuilder
          .source("sourceId1", "source", TopicParamName -> s"'${fixture.inputTopic}'".spel)
          .branchEnd("branchId1", "joinId1"),
        GraphBuilder
          .source("sourceId2", "source", TopicParamName -> s"'${fixture.inputTopic}'".spel)
          .branchEnd("branchId2", "joinId1"),
        GraphBuilder
          .join(
            "joinId1",
            "union",
            Some("unionOutput"),
            List(
              "branchId1" -> List("Output expression" -> "{a: #input}".spel),
              "branchId2" -> List("Output expression" -> "{a: #input}".spel)
            )
          )
          .emptySink(
            "sinkId1",
            "sink",
            TopicParamName     -> s"'${fixture.outputTopic}'".spel,
            SinkValueParamName -> "#unionOutput.a".spel
          )
      )

    runScenarioWithoutErrors(fixture, scenario) {
      val input = "test-input"

      kafkaClient.sendRawMessage(
        inputTopic,
        key = null,
        content = input.getBytes(),
        timestamp = inputTimestamp
      )

      val outputTimestamp = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head.timestamp
      outputTimestamp shouldBe inputTimestamp
    }
  }

  test("union, passing timestamp source to sink, single source with split") { fixture =>
    val inputTopic     = fixture.inputTopic
    val outputTopic    = fixture.outputTopic
    val inputTimestamp = System.currentTimeMillis()

    val scenario = ScenarioBuilder
      .streamingLite("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "source", TopicParamName -> s"'${fixture.inputTopic}'".spel)
          .split(
            "splitId1",
            GraphBuilder.buildSimpleVariable("varId1", "v1", "'value1'".spel).branchEnd("branch1", "joinId1"),
            GraphBuilder.buildSimpleVariable("varId2", "v2", "'value2'".spel).branchEnd("branch2", "joinId1")
          ),
        GraphBuilder
          .join(
            "joinId1",
            "union",
            Some("unionOutput"),
            List(
              "branch1" -> List("Output expression" -> "{a: #v1}".spel),
              "branch2" -> List("Output expression" -> "{a: #v2}".spel)
            )
          )
          .emptySink(
            "sinkId1",
            "sink",
            TopicParamName     -> s"'${fixture.outputTopic}'".spel,
            SinkValueParamName -> "#unionOutput.a".spel
          )
      )

    runScenarioWithoutErrors(fixture, scenario) {
      val input = "test-input"

      kafkaClient.sendRawMessage(
        inputTopic,
        key = null,
        content = input.getBytes(),
        timestamp = inputTimestamp
      )

      val outputTimestamp = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head.timestamp
      outputTimestamp shouldBe inputTimestamp
    }
  }

  test("should have same timestamp on source and sink") { fixture =>
    val inputTopic     = fixture.inputTopic
    val outputTopic    = fixture.outputTopic
    val scenario       = passThroughScenario(fixture)
    val inputTimestamp = System.currentTimeMillis()

    runScenarioWithoutErrors(fixture, scenario) {
      val input = "test-input"

      kafkaClient.sendRawMessage(
        inputTopic,
        key = null,
        content = input.getBytes(),
        timestamp = inputTimestamp
      )

      val outputTimestamp = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head.timestamp
      outputTimestamp shouldBe inputTimestamp
    }
  }

  test("should run scenario and pass data to output ") { fixture =>
    val inputTopic  = fixture.inputTopic
    val outputTopic = fixture.outputTopic
    val errorTopic  = fixture.errorTopic

    val scenario = ScenarioBuilder
      .streamingLite("test")
      .parallelism(2)
      .source("source", "source", TopicParamName -> s"'$inputTopic'".spel)
      .buildSimpleVariable("throw on 0", "someVar", "1 / #input.length".spel)
      .customNode("for-each", "splitVar", "for-each", "Elements" -> "{#input, 'other'}".spel)
      .emptySink(
        "sink",
        "sink",
        TopicParamName     -> s"'$outputTopic'".spel,
        SinkValueParamName -> "#splitVar + '-add'".spel
      )

    runScenarioWithoutErrors(fixture, scenario) {
      val input = "original"

      kafkaClient.sendMessage(inputTopic, input).futureValue
      kafkaClient.sendMessage(inputTopic, "").futureValue

      val messages = kafkaClient.createConsumer().consumeWithJson[String](outputTopic).take(2).map(_.message())
      messages shouldBe List("original-add", "other-add")

      val error = kafkaClient.createConsumer().consumeWithJson[KafkaExceptionInfo](errorTopic).take(1).head.message()
      error.nodeId shouldBe Some("throw on 0")
      error.processName shouldBe scenario.name
      error.exceptionInput shouldBe Some("1 / #input.length")
    }
  }

  test("correctly committing of offsets") { fixture =>
    val scenario: CanonicalProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {
      kafkaClient.sendMessage(fixture.inputTopic, "one").futureValue
      kafkaClient
        .createConsumer()
        .consumeWithJson[String](fixture.outputTopic)
        .take(1)
        .map(_.message()) shouldEqual List("one")
    }

    runScenarioWithoutErrors(fixture, scenario) {
      kafkaClient.sendMessage(fixture.inputTopic, "two").futureValue
      kafkaClient
        .createConsumer()
        .consumeWithJson[String](fixture.outputTopic)
        .take(2)
        .map(_.message()) shouldEqual List("one", "two")
    }
  }

  test("starts without error without kafka") { fixture =>
    val scenario: CanonicalProcess = passThroughScenario(fixture)

    val configWithFakeAddress = ConfigFactory.parseMap(
      Collections.singletonMap(KafkaConfigProperties.bootstrapServersProperty(), "not_exist.pl:9092")
    )
    runScenarioWithoutErrors(fixture, scenario, configWithFakeAddress) {
      // TODO: figure out how to wait for starting thread pool?
      Thread.sleep(100)
    }

  }

  test("handles immediate close") { fixture =>
    val scenario: CanonicalProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {
      // TODO: figure out how to wait for starting thread pool?
      Thread.sleep(100)
    }
  }

  test("detects fatal failure in close") { fixture =>
    val inputTopic  = fixture.inputTopic
    val outputTopic = fixture.outputTopic

    val failureMessage = "EXPECTED_TO_HAPPEN"

    val scenario: CanonicalProcess = passThroughScenario(fixture)
    val modelDataToUse             = modelData(adjustConfig(fixture.errorTopic, config))
    val jobData          = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    val liteKafkaJobData = LiteKafkaJobData(tasksCount = 1)

    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[Future, Input, Output](scenario, jobData, modelDataToUse)
      .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))
    val kafkaInterpreter = new KafkaTransactionalScenarioInterpreter(
      interpreter,
      scenario,
      jobData,
      liteKafkaJobData,
      modelDataToUse,
      preparer
    ) {
      override private[kafka] def createScenarioTaskRun(taskId: String): Task = {
        val original = super.createScenarioTaskRun(taskId)
        // we simulate throwing exception on shutdown
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
    val runResult = Using.resource(kafkaInterpreter) { interpreter =>
      val runResult = interpreter.run()
      // we wait for one message to make sure everything is already running
      kafkaClient.sendMessage(inputTopic, "dummy").futureValue
      kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).head
      runResult
    }
    Try(Await.result(runResult, 10 seconds)) match {
      case Failure(exception) =>
        exception.getMessage shouldBe failureMessage
      case result => throw new AssertionError(s"Should fail with completion exception, instead got: $result")
    }

  }

  test("detects fatal failure in run") { fixture =>
    val scenario: CanonicalProcess = passThroughScenario(fixture)
    val modelDataToUse             = modelData(adjustConfig(fixture.errorTopic, config))
    val jobData          = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    val liteKafkaJobData = LiteKafkaJobData(tasksCount = 1)

    var initAttempts = 0
    var runAttempts  = 0

    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[Future, Input, Output](scenario, jobData, modelDataToUse)
      .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))
    val kafkaInterpreter = new KafkaTransactionalScenarioInterpreter(
      interpreter,
      scenario,
      jobData,
      liteKafkaJobData,
      modelDataToUse,
      preparer
    ) {
      override private[kafka] def createScenarioTaskRun(taskId: String): Task = {
        val original = super.createScenarioTaskRun(taskId)
        // we simulate throwing exception on shutdown
        new Task {
          override def init(): Unit = {
            initAttempts += 1
            original.init()
          }

          override def run(): Unit = {
            runAttempts += 1
            if (runAttempts == 1) {
              throw new Exception("failure")
            }
          }

          override def close(): Unit = {
            original.close()
          }
        }
      }
    }
    val (runResult, attemptGauges, restartingGauges) = Using.resource(kafkaInterpreter) { interpreter =>
      val result = interpreter.run()
      // TODO: figure out how to wait for restarting tasks after failure?
      Thread.sleep(2000)
      // we have to get gauge here, as metrics are unregistered in close
      (result, metricsForName[Gauge[Int]]("task.attempt"), metricsForName[Gauge[Int]]("task.restarting"))
    }

    Await.result(runResult, 10 seconds)
    initAttempts should be > 1
    // we check if there weren't any errors in init causing that run next run won't be executed anymore
    runAttempts should be > 1
    attemptGauges.exists(_._2.getValue > 1)
    restartingGauges.exists(_._2.getValue > 1)
  }

  test("detects source failure") { fixture =>
    val scenario: CanonicalProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {
      kafkaClient
        .sendRawMessage(fixture.inputTopic, Array.empty, TestComponentProvider.FailingInputValue.getBytes)
        .futureValue
      val error =
        kafkaClient.createConsumer().consumeWithJson[KafkaExceptionInfo](fixture.errorTopic).take(1).head.message()

      error.nodeId shouldBe Some("source")
      error.processName shouldBe scenario.name
      // shouldn't it be just in error.message?
      error.exceptionInput.value should include(TestComponentProvider.SourceFailure.getMessage)
    }
  }

  test("source and kafka metrics are handled correctly") { fixture =>
    val inputTopic  = fixture.inputTopic
    val outputTopic = fixture.outputTopic

    val scenario: CanonicalProcess = passThroughScenario(fixture)

    runScenarioWithoutErrors(fixture, scenario) {

      val timestamp = Instant.now().minus(10, ChronoUnit.HOURS)

      val input = "original"

      kafkaClient.sendRawMessage(inputTopic, Array(), input.getBytes(), None, timestamp.toEpochMilli).futureValue
      kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).head

      forSomeMetric[Gauge[Long]]("eventtimedelay.minimalDelay")(_.getValue shouldBe withMinTolerance(10 hours))
      forSomeMetric[Histogram]("eventtimedelay.histogram")(_.getSnapshot.getMin shouldBe withMinTolerance(10 hours))
      forSomeMetric[Histogram]("eventtimedelay.histogram")(_.getCount shouldBe 1)

      forSomeMetric[Gauge[Long]]("records-lag-max")(_.getValue shouldBe 0)
      forEachNonEmptyMetric[Gauge[Double]]("outgoing-byte-total")(_.getValue should be > 0.0)
    }
  }

  private def forSomeMetric[T <: Metric: ClassTag](name: String)(action: T => Assertion): Unit = {
    val results = metricsForName[T](name).map(m => Try(action(m._2)))
    withClue(s"Available metrics: ${metricRegistry.getMetrics.keySet()}") {
      results should not be empty
    }
    results.exists(_.isSuccess) shouldBe true
  }

  private def forEachNonEmptyMetric[T <: Metric: ClassTag](name: String)(action: T => Any): Unit = {
    val metrics = metricsForName[T](name)
    withClue(s"Available metrics: ${metricRegistry.getMetrics.keySet()}") {
      metrics should not be empty
    }
    metrics.map(_._2).foreach(action)
  }

  private def metricsForName[T <: Metric: ClassTag](name: String): Iterable[(MetricName, T)] = {
    metricRegistry.getMetrics.asScala.collect {
      case (mName, metric: T) if mName.getKey == name => (mName, metric)
    }
  }

  private def runScenarioWithoutErrors[T](fixture: FixtureParam, scenario: CanonicalProcess, config: Config = config)(
      action: => T
  ): T = {
    val jobData          = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))
    val liteKafkaJobData = LiteKafkaJobData(tasksCount = 1)
    val configToUse      = adjustConfig(fixture.errorTopic, config)
    val modelDataToUse   = modelData(configToUse)
    val interpreter = ScenarioInterpreterFactory
      .createInterpreter[Future, Input, Output](scenario, jobData, modelDataToUse)
      .valueOr(errors => throw new IllegalArgumentException(s"Failed to compile: $errors"))
    val (runResult, output) = Using.resource(
      new KafkaTransactionalScenarioInterpreter(
        interpreter,
        scenario,
        jobData,
        liteKafkaJobData,
        modelDataToUse,
        preparer
      )
    ) { interpreter =>
      val result = interpreter.run()
      (result, action)
    }
    Await.result(runResult, 10 seconds)
    output
  }

  private def modelData(config: Config) =
    LocalModelData(
      config,
      TestComponentProvider.Components ::: LiteBaseComponentProvider.Components
    )

  private def adjustConfig(errorTopic: String, config: Config) = config
    .withValue("kafka.\"auto.offset.reset\"", fromAnyRef("earliest"))
    .withValue("exceptionHandlingConfig.topic", fromAnyRef(errorTopic))
    .withValue("waitAfterFailureDelay", fromAnyRef("1 millis"))

  private def passThroughScenario(fixtureParam: FixtureParam) = {
    ScenarioBuilder
      .streamingLite("test")
      .source("source", "source", TopicParamName -> s"'${fixtureParam.inputTopic}'".spel)
      .emptySink(
        "sink",
        "sink",
        TopicParamName     -> s"'${fixtureParam.outputTopic}'".spel,
        SinkValueParamName -> "#input".spel
      )
  }

  case class FixtureParam(inputTopic: String, outputTopic: String, errorTopic: String)

}
