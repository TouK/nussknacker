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
import io.circe.syntax.EncoderOps
import org.apache.kafka.clients.admin.NewTopic
import org.scalatest.freespec.AnyFreeSpecLike
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.test.{PatientScalaFutures, RestAssuredVerboseLoggingIfValidationFails}
import pl.touk.nussknacker.test.base.it.{NuItTest, WithSimplifiedConfigScenarioHelper}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.containers.WithDockerContainers
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{AdhocTestParametersRequest, TestSourceParameters}
import pl.touk.nussknacker.ui.api.testing.JsonSchemalessAdHocTestsSpec.{
  kafkaContainerAlias,
  sinkTopicName,
  sourceTopicName,
  WithSchemalessAdHocTestParameters
}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter.toScenarioGraph

import java.util.Arrays.asList
import java.util.Collections
import scala.jdk.CollectionConverters._

class JsonSchemalessAdHocTestsSpec
    extends AnyFreeSpecLike
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with RestAssuredVerboseLoggingIfValidationFails
    with PatientScalaFutures
    with WithAdHocTestsLogic
    with WithSchemalessAdHocTestParameters
    with WithDockerContainers
    with ForAllTestContainer
    with StrictLogging {

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

}

object JsonSchemalessAdHocTestsSpec {

  private[JsonSchemalessAdHocTestsSpec] trait WithSchemalessAdHocTestParameters extends WithAdHocTestParameters {

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
      TestSourceParameters(exampleScenarioSourceId, Map(inputParameterName -> Expression.json(validJson)))

    override protected def invalidParameters: TestSourceParameters =
      TestSourceParameters(exampleScenarioSourceId, Map(inputParameterName -> Expression.json(invalidJson)))

    override protected def parametersProvidedForDryRun: String = AdhocTestParametersRequest(
      sourceParameters = validParameters,
      scenarioGraph = toScenarioGraph(exampleScenario)
    ).asJson.toString()

    override protected def expectedValidationErrorsOnInvalidParametersJson: String =
      s"""
         |[
         |  {
         |    "typ": "ExpressionParserCompilationError",
         |    "message": "Failed to parse expression: expected } or , got 'a0.00}...' (line 3, column 45)",
         |    "description": "There is problem with expression in field Some(Value) - it could not be parsed.",
         |    "fieldName": "Value",
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
         |        "name": "Value",
         |        "typ": {
         |          "display": "Unknown",
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
         |        "label": "Value",
         |        "requiredParam": true,
         |        "category": "Standard"
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

    private val inputParameterName = ParameterName("Value")

  }

  private val sourceTopicName = "someInputTopic"

  private val sinkTopicName = "someOutputTopic"

  private val kafkaContainerAlias = "kafka"

}
