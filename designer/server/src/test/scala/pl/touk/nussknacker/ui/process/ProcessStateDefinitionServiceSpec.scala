package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{CategoriesConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestCategories.{Category1, Category2, TestCat, TestCat2}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.api.helpers.{MockDeploymentManager, MockManagerProvider}
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}

class ProcessStateDefinitionServiceSpec extends AnyFunSuite with Matchers {

  private val categoryService = ConfigProcessCategoryService(
    ConfigFactory.empty,
    Map(Streaming -> CategoriesConfig(List(Category1, Category2)), Fraud -> CategoriesConfig(List(TestCat, TestCat2)))
  )

  test("should fetch state definitions when definitions with the same name are unique") {
    val streamingProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    val definitions = testStateDefinitions(
      AdminUser("admin", "admin"),
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    definitions should have size 3

    val expectedCommon = streamingProcessStateDefinitionManager.stateDefinitions("COMMON")
    definitions should contain(
      UIStateDefinition(
        name = "COMMON",
        displayableName = expectedCommon.displayableName,
        icon = expectedCommon.icon,
        tooltip = expectedCommon.tooltip,
        categories = List(Category1, Category2, TestCat, TestCat2)
      )
    )

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_STREAMING",
        displayableName = expectedCustomStreaming.displayableName,
        icon = expectedCustomStreaming.icon,
        tooltip = expectedCustomStreaming.tooltip,
        categories = List(Category1, Category2)
      )
    )

    val expectedCustomFraud = fraudProcessStateDefinitionManager.stateDefinitions("CUSTOM_FRAUD")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_FRAUD",
        displayableName = expectedCustomFraud.displayableName,
        icon = expectedCustomFraud.icon,
        tooltip = expectedCustomFraud.tooltip,
        categories = List(TestCat, TestCat2)
      )
    )
  }

  test("should hide state definitions when user does not have permissions to category") {
    val streamingProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    val definitions = testStateDefinitions(
      CommonUser("user", "user", Map(Category1 -> Set(Permission.Read))),
      streamingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager
    )

    definitions should have size 2

    val expectedCommon = streamingProcessStateDefinitionManager.stateDefinitions("COMMON")
    definitions should contain(
      UIStateDefinition(
        name = "COMMON",
        displayableName = expectedCommon.displayableName,
        icon = expectedCommon.icon,
        tooltip = expectedCommon.tooltip,
        categories = List(Category1)
      )
    )

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_STREAMING",
        displayableName = expectedCustomStreaming.displayableName,
        icon = expectedCustomStreaming.icon,
        tooltip = expectedCustomStreaming.tooltip,
        categories = List(Category1)
      )
    )
  }

  test("should raise exception when definitions with the same name are NOT unique") {
    val streamingProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Non unique name", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    intercept[IllegalStateException] {
      testStateDefinitions(
        AdminUser("admin", "admin"),
        streamingProcessStateDefinitionManager,
        fraudProcessStateDefinitionManager
      )
    }.getMessage shouldBe "State definitions are not unique for states: COMMON"
  }

  private def createStateDefinitionManager(definitions: Map[StatusName, String]) =
    new OverridingProcessStateDefinitionManager(
      customStateDefinitions = definitions.map { case (name, displayableName) =>
        name -> StateDefinitionDetails(
          displayableName = displayableName,
          icon = UnknownIcon,
          tooltip = "dummy",
          description = s"Description for ${displayableName}"
        )
      },
      delegate = emptyStateDefinitionManager
    )

  private def testStateDefinitions(
      user: LoggedUser,
      streamingProcessStateDefinitionManager: OverridingProcessStateDefinitionManager,
      fraudProcessStateDefinitionManager: OverridingProcessStateDefinitionManager
  ): List[UIStateDefinition] = {
    val processingTypeDataMap =
      createProcessingTypeDataMap(streamingProcessStateDefinitionManager, fraudProcessStateDefinitionManager)
    val stateDefinitions = ProcessStateDefinitionService.createDefinitionsMappingUnsafe(processingTypeDataMap)
    val service = new ProcessStateDefinitionService(
      new MapBasedProcessingTypeDataProvider(Map.empty, (stateDefinitions, categoryService))
    )
    service.fetchStateDefinitions(user)
  }

  private val emptyStateDefinitionManager = new ProcessStateDefinitionManager {
    override def stateDefinitions: Map[StatusName, StateDefinitionDetails]        = Map.empty
    override def statusActions(stateStatus: StateStatus): List[ProcessActionType] = Nil
  }

  private def createProcessingTypeDataMap(
      streaming: ProcessStateDefinitionManager,
      fraud: ProcessStateDefinitionManager
  ): Map[ProcessingType, ProcessingTypeData] = {
    createProcessingTypeDataMap(
      Map(
        Streaming -> new MockDeploymentManager() {
          override def processStateDefinitionManager: ProcessStateDefinitionManager = streaming
        },
        Fraud -> new MockDeploymentManager() {
          override def processStateDefinitionManager: ProcessStateDefinitionManager = fraud
        },
      )
    )
  }

  private def createProcessingTypeDataMap(
      processingTypeToDeploymentManager: Map[ProcessingType, DeploymentManager]
  ): Map[ProcessingType, ProcessingTypeData] = {
    processingTypeToDeploymentManager.transform { case (_, deploymentManager) =>
      ProcessingTypeData.createProcessingTypeData(
        MockManagerProvider,
        deploymentManager,
        LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator),
        ConfigFactory.empty(),
        CategoriesConfig(List.empty)
      )
    }
  }

}
