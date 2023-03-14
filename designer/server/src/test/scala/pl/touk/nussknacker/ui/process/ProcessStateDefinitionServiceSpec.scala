package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, OverridingProcessStateDefinitionManager, ProcessStateDefinitionManager, StateDefinitionDetails}
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.TestCategories.{Category1, Category2, TestCat, TestCat2}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.process.processingtypedata.{MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import java.net.URI

class ProcessStateDefinitionServiceSpec extends AnyFunSuite with Matchers {

  private val categoryConfig = ConfigFactory.parseString(
    s"""
       |{
       |  categoriesConfig: {
       |    "$Category1": "$Streaming",
       |    "$Category2": "$Streaming",
       |    "$TestCat": "$Fraud",
       |    "$TestCat2": "$Fraud"
       |  }
       |}
       |""".stripMargin)

  private val categoryService = new ConfigProcessCategoryService(categoryConfig)

  test("should fetch state definitions when definitions with the same name are unique") {
    val streamingProcessStateDefinitionManager = new StreamingProcessStateDefinitionManager()
    val fraudProcessStateDefinitionManager = new FraudProcessStateDefinitionManager()

    val providerWithUniqueStateDefinitions = processingTypeDataProvider(
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    val service = new ProcessStateDefinitionService(providerWithUniqueStateDefinitions, categoryService)
    val definitions = service.fetchStateDefinitions()

    val expectedCommon = streamingProcessStateDefinitionManager.stateDefinitions("COMMON")
    definitions should contain(UIStateDefinition(
      name = "COMMON",
      displayableName = expectedCommon.displayableName,
      icon = expectedCommon.icon,
      tooltip = expectedCommon.tooltip,
      categories = List(Category1, Category2, TestCat, TestCat2)
    ))

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(UIStateDefinition(
      name = "CUSTOM_STREAMING",
      displayableName = expectedCustomStreaming.displayableName,
      icon = expectedCustomStreaming.icon,
      tooltip = expectedCustomStreaming.tooltip,
      categories = List(Category1, Category2)
    ))

    val expectedCustomFraud = fraudProcessStateDefinitionManager.stateDefinitions("CUSTOM_FRAUD")
    definitions should contain(UIStateDefinition(
      name = "CUSTOM_FRAUD",
      displayableName = expectedCustomFraud.displayableName,
      icon = expectedCustomFraud.icon,
      tooltip = expectedCustomFraud.tooltip,
      categories = List(TestCat, TestCat2)
    ))
  }

  test("should raise exception when definitions with the same name are NOT unique") {
    val streamingProcessStateDefinitionManager = new StreamingProcessStateDefinitionManager("Not unique name")
    val fraudProcessStateDefinitionManager = new FraudProcessStateDefinitionManager()

    val providerWithInvalidStateDefinitions = processingTypeDataProvider(
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    intercept[IllegalStateException]{
      ProcessStateDefinitionService.validate(providerWithInvalidStateDefinitions.all)
    }.getMessage should include("State definitions are not unique")
  }


  private class StreamingProcessStateDefinitionManager(displayableName: String = "Common",
                                                       icon: Option[URI] = None,
                                                       tooltip: Option[String] = None,
                                                       description: Option[String] = Some("This definition is common for all processing types")
                                                      ) extends OverridingProcessStateDefinitionManager(
    customStateDefinitions = Map(
      "COMMON" -> StateDefinitionDetails(
        displayableName = displayableName, icon = icon, tooltip = tooltip, description = description
      ),
      "CUSTOM_STREAMING" -> StateDefinitionDetails(
        displayableName = "Streaming", icon = None, tooltip = None, description = Some("This definition is specific for stremaing")
      ),
    )
  )

  private class FraudProcessStateDefinitionManager(displayableName: String = "Common",
                                                   icon: Option[URI] = None,
                                                   tooltip: Option[String] = None,
                                                   description: Option[String] = Some("This definition is common for all processing types")
                                                  ) extends OverridingProcessStateDefinitionManager(
    customStateDefinitions = Map(
      "COMMON" -> StateDefinitionDetails(
        displayableName = displayableName, icon = icon, tooltip = tooltip, description = description
      ),
      "CUSTOM_FRAUD" -> StateDefinitionDetails(
        displayableName = "Fraud", icon = None, tooltip = None, description = Some("This definition is specific for Fraud")
      ),
    )
  )

  private def processingTypeDataProvider(streaming: ProcessStateDefinitionManager,
                                         fraud: ProcessStateDefinitionManager): ProcessingTypeDataProvider[ProcessingTypeData] = {
    processingTypeDataProviderMap(Map(
      Streaming -> new MockDeploymentManager() {
        override def processStateDefinitionManager: ProcessStateDefinitionManager = streaming
      },
      Fraud -> new MockDeploymentManager() {
        override def processStateDefinitionManager: ProcessStateDefinitionManager = fraud
      },
    ))
  }

  private def processingTypeDataProviderMap(processingTypeToDeploymentManager: Map[String, DeploymentManager]) =
    new MapBasedProcessingTypeDataProvider(
      processingTypeToDeploymentManager.map { case (processingType, deploymentManager) =>
        processingType -> ProcessingTypeData(deploymentManager,
          LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator),
          TypeSpecificInitialData(StreamMetaData(Some(1))),
          Map.empty,
          Nil,
          ProcessingTypeUsageStatistics("stubManager", None))
      }
    )

}
