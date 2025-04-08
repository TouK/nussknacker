package pl.touk.nussknacker.ui.process.processingtype

import cats.data.Validated.Valid
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{JobsRecoverySettings, MetaDataInitializer}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.DeploymentManagerType
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ScenarioPropertyConfig}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.mock.MockDeploymentManager
import pl.touk.nussknacker.test.utils.domain.TestFactory.modelDependencies

class ProcessingTypeDataSpec extends AnyFunSuite with Matchers {

  test("should combine config and deployment manager properties with priority of config properties") {

    val propertyAFromConfig = ScenarioPropertyConfig.empty
      .copy(
        defaultValue = Some("default A config has higher priority"),
        label = Some("label A config has higher priority"),
      )
    val propertyBFromConfig = ScenarioPropertyConfig.empty
      .copy(
        defaultValue = Some("default B config has higher priority"),
        label = Some("label B config has higher priority")
      )

    val propertyBFromDeploymentData = ScenarioPropertyConfig.empty
      .copy(
        defaultValue = Some("default B"),
        label = Some("label B"),
      )

    val propertyCFromDeploymentData = ScenarioPropertyConfig.empty
      .copy(
        defaultValue = Some("default C"),
        label = Some("label C"),
      )

    val modelConfig = ConfigFactory
      .empty()
      .withValue("scenarioPropertiesConfig.property_A.defaultValue", fromAnyRef(propertyAFromConfig.defaultValue.get))
      .withValue("scenarioPropertiesConfig.property_A.label", fromAnyRef(propertyAFromConfig.label.get))
      .withValue("scenarioPropertiesConfig.property_B.defaultValue", fromAnyRef(propertyBFromConfig.defaultValue.get))
      .withValue("scenarioPropertiesConfig.property_B.label", fromAnyRef(propertyBFromConfig.label.get))

    val deploymentScenarioPropertiesConfig = Map(
      "property_B" -> propertyBFromDeploymentData,
      "property_C" -> propertyCFromDeploymentData
    )

    val processingTypeData = createProcessingTypeData(modelConfig, deploymentScenarioPropertiesConfig)

    processingTypeData.designerModelData.scenarioPropertiesConfig shouldBe Map(
      "property_A" -> propertyAFromConfig,
      "property_B" -> propertyBFromConfig,
      "property_C" -> propertyCFromDeploymentData
    )

  }

  private def createProcessingTypeData(
      modelConfig: Config,
      deploymentScenarioPropertiesConfig: Map[String, ScenarioPropertyConfig]
  ): ProcessingTypeData = {
    val mockManager = MockDeploymentManager.create()
    val deploymentData = new DeploymentData(
      DeploymentManagerType("mock"),
      Valid(mockManager),
      MetaDataInitializer("streaming", Map.empty[String, String]),
      deploymentScenarioPropertiesConfig,
      List.empty,
      JobsRecoverySettings.noRecovery,
      EngineSetupName("mock")
    )
    ProcessingTypeData.createProcessingTypeData(
      "dummy processingType",
      LocalModelData(
        modelConfig,
        List(ComponentDefinition("source", SourceFactory.noParamUnboundedStreamFactory[Any](new Source {}))),
        componentDefinitionExtractionMode = modelDependencies.componentDefinitionExtractionMode
      ),
      deploymentData,
      category = "dummy category",
      componentDefinitionExtractionMode = modelDependencies.componentDefinitionExtractionMode
    )
  }

}
