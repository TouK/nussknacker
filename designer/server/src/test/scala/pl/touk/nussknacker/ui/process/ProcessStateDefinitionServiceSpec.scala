package pl.touk.nussknacker.ui.process

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.StateDefinitionDetails.UnknownIcon
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessingType, Source, SourceFactory}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestCategories.{Category1, Category2}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.api.helpers.MockDeploymentManager
import pl.touk.nussknacker.ui.process.processingtype.{
  ProcessingTypeData,
  ProcessingTypeDataProvider,
  ValueWithPermission
}
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import sttp.client3.SttpBackend
import sttp.client3.akkahttp.AkkaHttpBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits._

class ProcessStateDefinitionServiceSpec extends AnyFunSuite with Matchers {

  private implicit val actorSystem: ActorSystem              = ActorSystem(getClass.getSimpleName)
  private implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(actorSystem)
  private implicit val processingTypeDeploymentService: ProcessingTypeDeploymentService = null

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
        categories = List(Category1, Category2)
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

    val expectedCustomFraud = fraudProcessStateDefinitionManager.stateDefinitions("CUSTOM_FRAUD")
    definitions should contain(
      UIStateDefinition(
        name = "CUSTOM_FRAUD",
        displayableName = expectedCustomFraud.displayableName,
        icon = expectedCustomFraud.icon,
        tooltip = expectedCustomFraud.tooltip,
        categories = List(Category2)
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
      ProcessingTypeDataProvider(
        Map(
          Streaming -> ValueWithPermission.userWithAccessRightsToCategory(Category1, Category1),
          Fraud     -> ValueWithPermission.userWithAccessRightsToCategory(Category2, Category2)
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
      Streaming -> createProcessingTypeData(
        Streaming,
        streaming,
        Category1
      ),
      Fraud -> createProcessingTypeData(
        Fraud,
        fraud,
        Category1
      ),
    )
  }

  private def createProcessingTypeData(
      processingType: ProcessingType,
      stateDefinitionManager: ProcessStateDefinitionManager,
      category: String
  ): ProcessingTypeData = {
    ProcessingTypeData.createProcessingTypeData(
      processingType,
      new FlinkStreamingDeploymentManagerProvider {
        override def createDeploymentManager(modelData: BaseModelData, config: Config)(
            implicit ec: ExecutionContext,
            actorSystem: ActorSystem,
            sttpBackend: SttpBackend[Future, Any],
            deploymentService: ProcessingTypeDeploymentService
        ): DeploymentManager =
          new MockDeploymentManager() {
            override def processStateDefinitionManager: ProcessStateDefinitionManager = stateDefinitionManager
          }
      },
      EngineSetupName("mock"),
      LocalModelData(
        ConfigFactory.empty(),
        List(ComponentDefinition("source", SourceFactory.noParamUnboundedStreamFactory[Any](new Source {})))
      ),
      ConfigFactory.empty(),
      category
    )
  }

}
