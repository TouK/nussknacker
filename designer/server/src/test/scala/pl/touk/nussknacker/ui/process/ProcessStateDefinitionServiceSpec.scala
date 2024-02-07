package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory
import pl.touk.nussknacker.ui.api.helpers.TestData.Categories.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType
import pl.touk.nussknacker.ui.api.helpers.TestData.ProcessingTypes.TestProcessingType.{Streaming, Streaming2}
import pl.touk.nussknacker.ui.api.helpers.{MockDeploymentManager, MockManagerProvider}
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ValueWithPermission}
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}

class ProcessStateDefinitionServiceSpec extends AnyFunSuite with Matchers {

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
        categories = List(Category1.stringify, Category2.stringify)
      )
    )

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_STREAMING",
        displayableName = expectedCustomStreaming.displayableName,
        icon = expectedCustomStreaming.icon,
        tooltip = expectedCustomStreaming.tooltip,
        categories = List(Category1.stringify)
      )
    )

    val expectedCustomFraud = fraudProcessStateDefinitionManager.stateDefinitions("CUSTOM_FRAUD")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_FRAUD",
        displayableName = expectedCustomFraud.displayableName,
        icon = expectedCustomFraud.icon,
        tooltip = expectedCustomFraud.tooltip,
        categories = List(Category2.stringify)
      )
    )
  }

  test("should hide state definitions when user does not have permissions to category") {
    val streamingProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_STREAMING" -> "Streaming"))
    val fraudProcessStateDefinitionManager =
      createStateDefinitionManager(Map("COMMON" -> "Common", "CUSTOM_FRAUD" -> "Fraud"))

    val definitions = testStateDefinitions(
      CommonUser("user", "user", Map(Category1.stringify -> Set(Permission.Read))),
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
        categories = List(Category1.stringify)
      )
    )

    val expectedCustomStreaming = streamingProcessStateDefinitionManager.stateDefinitions("CUSTOM_STREAMING")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_STREAMING",
        displayableName = expectedCustomStreaming.displayableName,
        icon = expectedCustomStreaming.icon,
        tooltip = expectedCustomStreaming.tooltip,
        categories = List(Category1.stringify)
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
          description = s"Description for $displayableName"
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
      ProcessingTypeDataProvider(
        Map(
          Streaming.stringify -> ValueWithPermission
            .userWithAccessRightsToCategory(Category1.stringify, Category1.stringify),
          Streaming2.stringify -> ValueWithPermission
            .userWithAccessRightsToCategory(Category2.stringify, Category2.stringify)
        ),
        stateDefinitions
      )
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
    Map(
      Streaming.stringify -> createProcessingTypeData(
        new MockDeploymentManager() {
          override def processStateDefinitionManager: ProcessStateDefinitionManager = streaming
        },
        Streaming,
        Category1
      ),
      Streaming2.stringify -> createProcessingTypeData(
        new MockDeploymentManager() {
          override def processStateDefinitionManager: ProcessStateDefinitionManager = fraud
        },
        Streaming2,
        Category2
      ),
    )
  }

  private def createProcessingTypeData(
      deploymentManager: DeploymentManager,
      processingType: TestProcessingType,
      category: TestCategory
  ): ProcessingTypeData = {
    ProcessingTypeData.createProcessingTypeData(
      processingType.stringify,
      MockManagerProvider,
      deploymentManager,
      LocalModelData(ConfigFactory.empty(), List.empty),
      ConfigFactory.empty(),
      category.stringify
    )
  }

}
