package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, FailedStateStatus, OverridingProcessStateDefinitionManager, ProcessStateDefinitionManager, StateDefinitionDetails, StateStatus}
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.TestCategories.{Category1, Category2, TestCat, TestCat2}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionServiceSpec.{createStateDefinitionManager, testStateDefinitions}
import pl.touk.nussknacker.ui.process.processingtypedata.{MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

class ProcessStateDefinitionServiceSpec extends AnyFunSuite with Matchers {

  test("should fetch state definitions when definitions with the same name are unique") {
    val streamingProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    val definitions = testStateDefinitions(
      AdminUser("admin", "admin"),
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    definitions should have size 3

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

  test("should hide state definitions when user does not have permissions to category"){
    val streamingProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    val definitions = testStateDefinitions(
      CommonUser("user", "user", Map(Category1 -> Set(Permission.Read))),
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    definitions should have size 2

    val expectedCommon = streamingProcessStateDefinitionManager.stateDefinitions("COMMON")
    definitions should contain(UIStateDefinition(
      name = "COMMON",
      displayableName = expectedCommon.displayableName,
      icon = expectedCommon.icon,
      tooltip = expectedCommon.tooltip,
      categories = List(Category1)
    ))

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(UIStateDefinition(
      name = "CUSTOM_STREAMING",
      displayableName = expectedCustomStreaming.displayableName,
      icon = expectedCustomStreaming.icon,
      tooltip = expectedCustomStreaming.tooltip,
      categories = List(Category1)
    ))
  }

  test("should raise exception when definitions with the same name are NOT unique") {
    val streamingProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Non unique name", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager = createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    intercept[IllegalStateException]{
      testStateDefinitions(
        AdminUser("admin", "admin"),
        streamingProcessStateDefinitionManager,
        fraudProcessStateDefinitionManager
      )
    }.getMessage should include("State definitions are not unique")
  }

}

object ProcessStateDefinitionServiceSpec {

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

  private val emptyStateDefinitionManager = new ProcessStateDefinitionManager {
    override def stateDefinitions: Map[StatusName, StateDefinitionDetails] = Map.empty
    override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = Nil
    override def mapActionToStatus(stateAction: Option[ProcessActionType]): StateStatus = FailedStateStatus("dummy")
  }

  def createStateDefinitionManager(definitions: Map[String, String]) = new OverridingProcessStateDefinitionManager(
    customStateDefinitions = definitions.map { case (name, displayableName) =>
      name -> StateDefinitionDetails(
        displayableName = displayableName, icon = None, tooltip = None, description = Some(s"Description for ${displayableName}")
      )
    },
    delegate = emptyStateDefinitionManager
  )

  def testStateDefinitions(user: LoggedUser, streamingProcessStateDefinitionManager: OverridingProcessStateDefinitionManager, fraudProcessStateDefinitionManager: OverridingProcessStateDefinitionManager): List[UIStateDefinition] = {
    val typeToConfig = processingTypeDataProvider(streamingProcessStateDefinitionManager, fraudProcessStateDefinitionManager)
    val service = new ProcessStateDefinitionService(typeToConfig, categoryService)
    ProcessStateDefinitionService.checkUnsafe(typeToConfig.all)
    service.fetchStateDefinitions(user)
  }

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
