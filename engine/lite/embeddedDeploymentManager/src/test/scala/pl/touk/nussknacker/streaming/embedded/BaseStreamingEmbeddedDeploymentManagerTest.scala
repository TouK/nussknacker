package pl.touk.nussknacker.streaming.embedded

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromMap}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, TopicName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.embedded.EmbeddedDeploymentManager
import pl.touk.nussknacker.engine.embedded.streaming.StreamingDeploymentStrategy
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.{FailingContextClassloader, VeryPatientScalaFutures}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.jdk.CollectionConverters._

trait BaseStreamingEmbeddedDeploymentManagerTest
    extends AnyFunSuite
    with SchemaRegistryMixin
    with Matchers
    with VeryPatientScalaFutures {

  override protected def schemaRegistryClient: SchemaRegistryClient = MockSchemaRegistry.schemaRegistryMockClient

  sealed case class FixtureParam(
      deploymentManager: DeploymentManager,
      modelData: ModelData,
      inputTopic: TopicName.ForSource,
      outputTopic: TopicName.ForSink
  ) {

    def deployScenario(scenario: CanonicalProcess): Unit = {
      val version = ProcessVersion.empty.copy(processName = scenario.name)
      deploymentManager
        .processCommand(
          DMRunDeploymentCommand(
            version,
            DeploymentData.empty,
            scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
    }

  }

  protected def generateInputTopicName: TopicName.ForSource =
    TopicName.ForSource(s"input-${UUID.randomUUID().toString}")

  protected def generateOutputTopicName: TopicName.ForSink =
    TopicName.ForSink(s"output-${UUID.randomUUID().toString}")

  private val defaultJsonSchema =
    """
      |{
      |  "$schema": "https://json-schema.org/draft/2020-12/schema",
      |  "$id": "https://example.com/product.schema.json",
      |  "title": "Product",
      |  "description": "A product from Acme's catalog",
      |  "type": "object",
      |  "properties": {
      |    "productId": {
      |      "description": "The unique identifier for a product",
      |      "type": "integer"
      |    }
      |  },
      |  "required": [ "productId" ]
      |}
      |""".stripMargin

  protected def wrapInFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  protected def prepareFixture(
      inputTopic: TopicName.ForSource = generateInputTopicName,
      outputTopic: TopicName.ForSink = generateOutputTopicName,
      initiallyDeployedScenarios: List[DeployedScenarioData] = List.empty,
      jsonSchema: String = defaultJsonSchema
  ): FixtureParam = {
    registerJsonSchema(inputTopic.toUnspecialized, jsonSchema, isKey = false)
    registerJsonSchema(outputTopic.toUnspecialized, jsonSchema, isKey = false)

    kafkaClient.createTopic(inputTopic.name, partitions = 1)
    kafkaClient.createTopic(outputTopic.name, partitions = 1)

    val configToUse = config
      .withValue("exceptionHandlingConfig.topic", fromAnyRef("errors"))

    val kafkaComponentProviderConfig = ConfigFactory
      .empty()
      .withValue(
        "kafkaProperties",
        fromMap(
          Map[String, Any](
            //        This timeout controls how long the kafka producer initialization in pl.touk.nussknacker.engine.lite.kafka.KafkaSingleScenarioTaskRun.init.
            //        So it has to be set to a reasonably low value for the restarting test to finish before ScalaFutures patience runs out.
            "max.block.ms"           -> 2000,
            "request.timeout.ms"     -> 2000,
            "default.api.timeout.ms" -> 2000,
            "auto.offset.reset"      -> "earliest"
          ).asJava
        )
      )

    val kafkaComponents = new MockLiteKafkaComponentProvider()
      .create(kafkaComponentProviderConfig, ProcessObjectDependencies.withConfig(config))

    val modelData         = LocalModelData(configToUse, kafkaComponents)
    val deploymentService = new ProcessingTypeDeployedScenariosProviderStub(initiallyDeployedScenarios)
    wrapInFailingLoader {
      val strategy = new StreamingDeploymentStrategy {
        override protected def handleUnexpectedError(version: ProcessVersion, throwable: Throwable): Unit =
          throw new AssertionError("Should not happen...")
      }
      strategy.open(modelData, LiteEngineRuntimeContextPreparer.noOp)
      val manager = new EmbeddedDeploymentManager(modelData, deploymentService, strategy)
      FixtureParam(manager, modelData, inputTopic, outputTopic)
    }
  }

}
