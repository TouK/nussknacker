package pl.touk.nussknacker.ui.api.testing

import com.dimafeng.testcontainers.{
  Container,
  ForAllTestContainer,
  KafkaContainer,
  MultipleContainers,
  SchemaRegistryContainer
}
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.config.ConfigValueFactory.fromMap
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json, JsonObject}
import io.circe.parser._
import io.circe.syntax.EncoderOps
import io.restassured.RestAssured.`given`
import io.restassured.module.scala.RestAssuredSupport.AddThenToResponse
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.json.decoders.TypingResultDecoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.inputParamName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ContentTypes
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{
  NuRestAssureExtensions,
  PatientScalaFutures,
  RestAssuredVerboseLoggingIfValidationFails
}
import pl.touk.nussknacker.test.ProcessUtils.convertToAnyShouldWrapper
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.containers.WithDockerContainers
import pl.touk.nussknacker.test.processes.WithScenarioActivitySpecAsserts.UsersBasicAuth
import pl.touk.nussknacker.ui.api.ScenarioValidationRequest
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.TestSourceParameters
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.ScenarioTestData
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Validate.ScenarioTestValidationRequest
import pl.touk.nussknacker.ui.api.testing.SchemalessKafkaJsonTypeTests.{
  ageVariable,
  getScenarioWithDataSample,
  isAdultVariable,
  kafkaContainerAlias,
  nameVariable,
  sinkTopicName,
  sourceTopicName,
  variablesNodeName,
  WithSchemalessAdHocTestsLogic
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.{Collections, UUID}
import java.util.Arrays.asList
import scala.jdk.CollectionConverters._

class SchemalessKafkaJsonTypeTests
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with WithAdHocTestsLogic
    with WithSchemalessAdHocTestsLogic
    with WithAdHocInvalidParametersTestsLogic
    with WithDockerContainers
    with ForAllTestContainer
    with StrictLogging
    with NuRestAssureExtensions {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    createKafkaTopics()
  }

  "The endpoint for adhoc validate should" - {
    "return no errors on valid parameters" in {
      shouldValidateParametersProperly()
    }
    "return errors if passed parameter is not valid" in {
      shouldReturnErrorsForInvalidParameters()
    }
  }

  "The endpoint for adhoc test run should" - {
    "run scenario and return result" in {
      shouldProperlyRunAdHocTest()
    }
  }

  "The endpoint for adhoc test parameters should" - {
    "return test parameters" in {
      shouldProperlyGetTestParameters()
    }
  }

  "The endpoint for process validation should" - {
    "validate scenario properly with Json data and return proper typing" in {
      val typedJsonDataSamples = List("{}", "[]", "null").map(getScenarioWithDataSample)
      typedJsonDataSamples.foreach { scenarioWithEmptyDataSample =>
        val request =
          ScenarioValidationRequest(
            scenarioWithEmptyDataSample.name,
            CanonicalProcessConverter.toScenarioGraph(scenarioWithEmptyDataSample)
          ).asJson.toString()

        val response = given()
          .applicationState {
            createSavedScenario(scenarioWithEmptyDataSample)
          }
          .when()
          .basicAuthAllPermUser()
          .jsonBody(request)
          .post(s"$nuDesignerHttpAddress/api/processValidation/${scenarioWithEmptyDataSample.name}")
          .getBody
          .asString()

        val typingResult = getTypingResultFromValidationResponse(response)
        typingResult("input") shouldBe Typed.json
        typingResult(variablesNodeName) match {
          case TypedObjectTypingResult(fields, _, _) =>
            fields(nameVariable) shouldBe Typed.json
            fields(ageVariable) shouldBe Typed.typedClass[Int]
            fields(isAdultVariable) shouldBe Typed.typedClass[Boolean]
          case _ => fail
        }
      }
    }
  }

  "The endpoint for test data generation should" - {
    "return error if no live data available" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          testDataGenerationRequest(
            Encoder[ScenarioGraph].apply(toScenarioGraph(exampleScenario)).toString(),
            numberOfSamples = 3
          )
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/generatedTestData")
        .Then()
        .statusCode(404)
        .equalsPlainBody("Could not provide a sample of test data. Possible cause: no live data available")
    }
  }

  "The endpoint for test with live data should" - {
    "return error if no live data available" in {
      given()
        .applicationState {
          createSavedScenario(exampleScenario)
        }
        .when()
        .basicAuthAllPermUser()
        .jsonBody(
          ScenarioTestValidationRequest(
            testData = ScenarioTestData.WithGeneratedData(10),
            scenarioGraph = toScenarioGraph(exampleScenario)
          ).asJson.toString()
        )
        .post(s"$nuDesignerHttpAddress/api/scenarioTesting/${exampleScenario.name}/performTest")
        .Then()
        .statusCode(404)
        .equalsPlainBody("Could not provide a sample of test data. Possible cause: no live data available")
    }
  }

  protected val kafkaContainer: KafkaContainer =
    KafkaContainer().configure { self =>
      self.setNetwork(network)
      self.setNetworkAliases(asList(kafkaContainerAlias))
      self.setPortBindings(List("8070:9093").asJava)
    }

  private val schemaRegistryContainer: SchemaRegistryContainer =
    SchemaRegistryContainer(network, kafkaContainerAlias).configure { self =>
      self.setPortBindings(List("8069:8081").asJava)
    }

  override def container: Container = MultipleContainers(kafkaContainer, schemaRegistryContainer)

  override def designerRawConfig: Config = super.designerRawConfig
    .withoutPath("scenarioTypes.streaming.modelConfig.components.kafka.disabled")
    .withValue(
      "scenarioTypes.streaming.modelConfig.components.kafka.config.kafkaProperties",
      fromMap(Map("bootstrap.servers" -> "localhost:8070", "schema.registry.url" -> "http://localhost:8069").asJava)
    )
    .withValue(
      "scenarioTypes.streaming.modelConfig.components.kafka.config.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
      ConfigValueFactory.fromAnyRef(true)
    )

  lazy val defaultKafkaConfig: KafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> kafkaContainer.bootstrapServers)),
    kafkaEspProperties = None,
  )

  private def createKafkaTopics(): Unit = {
    val sourceTopic = new NewTopic(sourceTopicName, Collections.emptyMap())
    val sinkTopic   = new NewTopic(sinkTopicName, Collections.emptyMap())
    KafkaUtils.usingAdminClient(defaultKafkaConfig) {
      _.createTopics(List(sourceTopic, sinkTopic).asJava)
    }
  }

  private def getTypingResultFromValidationResponse(jsonString: String): Map[String, TypingResult] = {
    val decoder                                             = new TypingResultDecoder(getClass.getClassLoader.loadClass)
    implicit val typingResultDecoder: Decoder[TypingResult] = decoder.decodeTypingResults

    val parsed = for {
      json        <- parse(jsonString)
      nodeResults <- json.hcursor.downField("nodeResults").as[JsonObject]
    } yield {
      nodeResults.toMap.flatMap { case (_, nodeJson) =>
        val cursor = nodeJson.hcursor.downField("variableTypes")
        cursor.keys.getOrElse(Nil).map { key =>
          key -> cursor.downField(key).focus.getOrElse(Json.Null)
        }
      }
    }

    parsed
      .getOrElse(throw new IllegalStateException("Could not parse validation response"))
      .map { case (name, jsonValue) =>
        val result = typingResultDecoder
          .decodeJson(jsonValue)
          .getOrElse(throw new IllegalStateException("Could not parse typing result"))
        name -> result
      }
  }

  private def testDataGenerationRequest(
      scenarioGraphStr: String,
      numberOfSamples: Int,
  ) =
    s"""{
       |  "scenarioGraph": $scenarioGraphStr,
       |  "numberOfSamples": $numberOfSamples
       |}""".stripMargin

}

object SchemalessKafkaJsonTypeTests {

  private[SchemalessKafkaJsonTypeTests] trait WithSchemalessAdHocTestsLogic
      extends WithAdHocTestsLogic
      with WithAdHocInvalidParametersTestsLogic {
    self: WithSimplifiedConfigScenarioHelper with NuItTest =>

    override protected def exampleScenarioSourceId: String = "start"

    override protected def exampleScenario: CanonicalProcess = {
      ScenarioBuilder
        .streaming("without-schema")
        .parallelism(1)
        .source(
          exampleScenarioSourceId,
          "kafka",
          "Topic"        -> s"'$sourceTopicName'".spel,
          "Content type" -> "'JSON'".spel,
          "Data sample"  -> Expression.json("{\"name\": \"Tom\"}")
        )
        .emptySink(
          "end",
          "kafka",
          "Key"                   -> "".spel,
          "Raw editor"            -> "true".spel,
          "Value"                 -> "#input".spel,
          "Topic"                 -> s"'$sinkTopicName'".spel,
          "Content type"          -> "'JSON'".spel,
          "Value validation mode" -> s"'${ValidationMode.lax.name}'".spel
        )
    }

    override protected def validParameters: TestSourceParameters =
      TestSourceParameters(exampleScenarioSourceId, Map(inputParamName -> Expression.json(validJson)))

    override protected def invalidParameters: TestSourceParameters =
      TestSourceParameters(exampleScenarioSourceId, Map(inputParamName -> Expression.json(invalidJson)))

    override protected def parametersProvidedForDryRun: String =
      ScenarioTestValidationRequest(
        testData = ScenarioTestData.WithParameters(validParameters),
        scenarioGraph = toScenarioGraph(exampleScenario)
      ).asJson.toString()

    override protected def expectedValidationErrorsOnInvalidParametersJson: String =
      s"""
         |[
         |  {
         |    "typ": "ExpressionParserCompilationError",
         |    "message": "Failed to parse expression: expected } or , got 'a0.00}...' (line 3, column 45)",
         |    "description": "There is problem with expression in field Some(Input) - it could not be parsed.",
         |    "fieldName": "Input",
         |    "errorType": "SaveAllowed",
         |    "details": null
         |  }
         |]""".stripMargin

    override protected def expectedTestParametersJson: String = {
      s"""
         |[
         |  {
         |    "sourceId": "$exampleScenarioSourceId",
         |    "parameters": [
         |      {
         |        "name": "Input",
         |        "typ": {
         |          "display": "Json",
         |          "type": "Unknown",
         |          "refClazzName": "java.lang.Object",
         |          "params": []
         |        },
         |        "editors": [
         |          {
         |            "type": "JsonParameterEditor"
         |          }
         |        ],
         |        "defaultValue": {
         |          "language": "json",
         |          "expression": "{\\n  \\"name\\" : \\"Tom\\"\\n}"
         |        },
         |        "additionalVariables": {},
         |        "variablesToHide": [],
         |        "branchParam": false,
         |        "hintText": null,
         |        "label": "Input",
         |        "requiredParam": true,
         |        "category": "Standard",
         |        "changesCanReloadParameters": false
         |      }
         |    ]
         |  }
         |]
         |""".stripMargin
    }

    private val validJson = """|[
                               |  {
                               |    "products": [
                               |      {"id": 1, "name": "Laptop", "price": 1200.00},
                               |      {"id": 2, "name": "Smartphone", "price": 800.50},
                               |      {"id": 3, "name": "Tablet", "price": 450.75}
                               |    ]
                               |  },
                               |  {
                               |    "someObject": {
                               |      "someString": "some string value",
                               |      "someBoolean": true,
                               |      "someNumber": 21.37
                               |    }
                               |  }
                               |]""".stripMargin

    private val invalidJson = """|{
                                 |  "products": [
                                 |    {"id": 1, "name": "Laptop", "price": 120a0.00},
                                 |    {"id": 2, "name": "Smartphone", "price": 800.50},
                                 |    {"id": 3, "name": "Tablet", "price": 450.75}
                                 |  ]
                                 |}""".stripMargin

  }

  private val sourceTopicName = "someInputTopic"

  private val sinkTopicName = "someOutputTopic"

  private val kafkaContainerAlias = "kafka"

  private val variablesNodeName = "vars"
  private val nameVariable      = "name"
  private val ageVariable       = "age"
  private val isAdultVariable   = "isAdult"

  private def getScenarioWithDataSample(dataSample: String) =
    ScenarioBuilder
      .streaming(UUID.randomUUID().toString)
      .parallelism(1)
      .additionalFields(properties = Map("environment" -> "someNotEmptyString"))
      .source(
        "start",
        "kafka",
        KafkaUniversalComponentTransformer.topicParamName.value       -> s"'$sourceTopicName'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value -> s"'${ContentTypes.JSON.toString}'".spel,
        KafkaUniversalComponentTransformer.dataSampleParamName.value  -> Expression.json(dataSample)
      )
      .buildVariable(
        "bv1",
        variablesNodeName,
        nameVariable    -> "#input[0]['name']".spel,
        ageVariable     -> "#input[0]['age'].toInteger()".spel,
        isAdultVariable -> "#input[0]['age'].toInteger() >= 18".spel
      )
      .emptySink(
        "end",
        "kafka",
        KafkaUniversalComponentTransformer.sinkKeyParamName.value            -> "".spel,
        KafkaUniversalComponentTransformer.sinkRawEditorParamName.value      -> "true".spel,
        KafkaUniversalComponentTransformer.sinkValueParamName.value          -> "#input".spel,
        KafkaUniversalComponentTransformer.topicParamName.value              -> s"'$sinkTopicName'".spel,
        KafkaUniversalComponentTransformer.contentTypeParamName.value        -> s"'${ContentTypes.JSON.toString}'".spel,
        KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel
      )

}
