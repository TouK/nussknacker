package pl.touk.nussknacker.gpt

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.{ObjectSchema, StringSchema}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.util.test.RequestResponseTestScenarioRunner._
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SinkRawEditorParamName, SinkValueParamName}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.gpt.ChatGptComponent.promptParameter
import pl.touk.nussknacker.gpt.GptComponentsProvider.mockGptServicePropertyName
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ValidatedValuesDetailedMessage}

class ChatGptComponentIntegrationTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage
  with ValidatedValuesDetailedMessage with LazyLogging {

  test("integration with OpenAI") {
    val testRunner = TestScenarioRunner.requestResponseBased(
      ConfigFactory.parseString(
        """components {
          |  gpt {
          |    openaAIApiKey: dumb
          |    openaAIApiKey: ${?OPENAI_API_KEY}
          |  }
          |}""".stripMargin)).build()

    val schema = ObjectSchema.builder()
      .addPropertySchema("prompt", StringSchema.builder().build())
      .addRequiredProperty("prompt")
      .build()
    logger.debug(s"Input Schema: $schema")

    val baseProperties = Map(
      "inputSchema" -> schema.toString,
      "outputSchema" -> "{}")
    val properties = if (!sys.env.contains("OPENAI_API_KEY")) {
      baseProperties + (mockGptServicePropertyName -> "true")
    } else {
      baseProperties
    }
    val scenario = ScenarioBuilder
      .requestResponse("test")
      .additionalFields(properties = properties)
      .source("request", "request")
      .enricher("chat", "chatResponse", ChatGptComponent.serviceName,
        promptParameter.name -> Expression("spelTemplate", "Say: #{#input.prompt}")
      )
      .emptySink("response", "response", SinkRawEditorParamName -> "true",
        SinkValueParamName ->
          """{
            |  prompt: #input.prompt,
            |  response: #chatResponse
            |}""".stripMargin)

    testRunner.runWithRequests(scenario) { invoke =>
      val prompt = "hello"
      val response = invoke(HttpRequest(HttpMethods.POST, entity =
        s"""{
           |  "prompt": "$prompt"
           |}""".stripMargin)).rightValue
      response.hcursor.downField("prompt").as[String].rightValue shouldEqual prompt
      val chatResponse = response.hcursor.downField("response").as[String].rightValue
      logger.debug(s"Returned response: $chatResponse")
    }.validValue
  }

}
