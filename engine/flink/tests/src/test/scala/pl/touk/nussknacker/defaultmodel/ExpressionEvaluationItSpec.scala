package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.{parser, Json}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.TopicName.ForSource
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

class ExpressionEvaluationItSpec extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  test("should produce a message when variable with empty spelTemplate expression is passed") {
    val inputTopic  = createRandomTopic("input-topic")
    val outputTopic = createRandomTopic("output-topic")

    sendAsJson("message", ForSource(inputTopic), Instant.now.toEpochMilli)

    val scenario = createScenario(inputTopic, outputTopic, Expression.spelTemplate(""))
    run(scenario) {
      val parsedOutput: Json = consumeOneMessage(outputTopic)

      parsedOutput shouldBe Json.fromString("message-empty")
    }
  }

  test("should produce a message when variable with non-empty spelTemplate expression is passed") {
    val inputTopic  = createRandomTopic("input-topic")
    val outputTopic = createRandomTopic("output-topic")

    sendAsJson("message", ForSource(inputTopic), Instant.now.toEpochMilli)

    val scenario = createScenario(inputTopic, outputTopic, Expression.spelTemplate("value"))
    run(scenario) {
      val parsedOutput: Json = consumeOneMessage(outputTopic)

      parsedOutput shouldBe Json.fromString("message-value")
    }
  }

  test("should produce a message when variable with non-empty string spel expression is passed") {
    val inputTopic  = createRandomTopic("input-topic")
    val outputTopic = createRandomTopic("output-topic")

    sendAsJson("message", ForSource(inputTopic), Instant.now.toEpochMilli)

    val scenario = createScenario(inputTopic, outputTopic, Expression.spel("'value'"))
    run(scenario) {
      val parsedOutput: Json = consumeOneMessage(outputTopic)

      parsedOutput shouldBe Json.fromString("message-value")
    }
  }

  test("should throw a compilation error when variable with empty spel expression is passed") {
    val inputTopic  = createRandomTopic("input-topic")
    val outputTopic = createRandomTopic("output-topic")

    val scenario = createScenario(inputTopic, outputTopic, Expression.spel(""))

    val caughtException = intercept[IllegalArgumentException] {
      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        registrar.register(env, scenario, ProcessVersion.empty, DeploymentData.empty)
      }
    }

    caughtException.getMessage should startWith(
      "Compilation errors: EmptyMandatoryParameter(Field: $expression is mandatory and can not be empty,Please fill field for this parameter,$expression,id)"
    )
  }

  private def createRandomTopic(prefix: String) = {
    val topicName = prefix + UUID.randomUUID().toString
    kafkaClient.createTopic(topicName, 1)
    topicName
  }

  private def createScenario(
      inputTopic: String,
      outputTopic: String,
      variableExpression: Expression,
  ) = {
    ScenarioBuilder
      .streaming("test-scenario")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$inputTopic'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> "'JSON'".spel
      )
      .buildSimpleVariable("id", "varName", variableExpression)
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$outputTopic'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> "'JSON'".spel,
        KafkaUniversalComponentTransformer.sinkKeyParamName.value     -> "".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value -> Expression
          .spel("#input + (#varName.length == 0 ? '-empty' : '-' + #varName)"),
      )
  }

  private def consumeOneMessage(outputTopic: String) = {
    val outputRecord = kafkaClient.createConsumer().consumeWithConsumerRecord(outputTopic).take(1).head
    val parsedOutput = parser
      .parse(new String(outputRecord.value(), StandardCharsets.UTF_8))
      .fold(throw _, identity)
    parsedOutput
  }

}
