package pl.touk.nussknacker.ui.api.helpers

import java.net.URI
import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.all._
import cats.syntax.semigroup._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Encoder, Json, Printer, parser}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.{GraphProcess, ProcessActionType, ProcessManager}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingProcessManagerProvider
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.deployment.CustomActionRequest
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReader
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{DefaultAuthenticationConfiguration, LoggedUser}
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion

import scala.concurrent.Future


trait EspItTest extends LazyLogging with WithHsqlDbTesting with TestPermissions { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers with ScalaFutures =>

  override def testConfig: Config = ConfigWithScalaVersion.config

  val env = "test"
  val attachmentsPath = "/tmp/attachments" + System.currentTimeMillis()

  val processRepository = newProcessRepository(db)
  val processAuthorizer = new AuthorizeProcess(processRepository)

  val writeProcessRepository = newWriteProcessRepository(db)
  val subprocessRepository = newSubprocessRepository(db)
  val actionRepository = newActionProcessRepository(db)
  val processActivityRepository = newProcessActivityRepository(db)

  val typesForCategories = new ProcessTypesForCategories(testConfig)

  val existingProcessingType = "streaming"

  protected def createProcessManager(): MockProcessManager = new MockProcessManager

  lazy val processManager = createProcessManager()

  val processManagerProvider = new FlinkStreamingProcessManagerProvider {
    override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = processManager
  }
  val processChangeListener = new TestProcessChangeListener()
  def createManagementActorRef: ActorRef = {
    system.actorOf(ManagementActor.props(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> processManager),
      processRepository,
      actionRepository,
      TestFactory.sampleResolver,
      processChangeListener), "management")
  }
  val managementActor: ActorRef = createManagementActorRef
  val processService = new ProcessService(managementActor, processRepository, actionRepository, writeProcessRepository)
  val newProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider("streaming" ->  ProcessTestData.processDefinition),
    mapProcessingTypeDataProvider("streaming" -> (_ => StreamMetaData(None))),
    mapProcessingTypeDataProvider("streaming" -> Map.empty)
  )

  val featureTogglesConfig = FeatureTogglesConfig.create(testConfig)
  val typeToConfig = ProcessingTypeDataReader.loadProcessingTypeData(testConfig)

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  val processesRoute = new ProcessesResources(
    processRepository = processRepository,
    writeRepository = writeProcessRepository,
    processService = processService,
    processValidation = processValidation,
    processResolving = processResolving,
    typesForCategories = typesForCategories,
    newProcessPreparer = newProcessPreparer,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener,
    typeToConfig = typeToConfig
  )

  val authenticationConfig = DefaultAuthenticationConfiguration.create(testConfig)
  val analyticsConfig = AnalyticsConfig(testConfig)

  val usersRoute = new UserResources(typesForCategories)
  val settingsRoute = new SettingsResources(featureTogglesConfig, typeToConfig, authenticationConfig, analyticsConfig)

  val processesExportResources = new ProcessesExportResources(processRepository, processActivityRepository, processResolving)

  val processingTypeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig)
  val definitionResources = new DefinitionResources(
    modelDataProvider = mapProcessingTypeDataProvider(existingProcessingType -> processingTypeConfig.toModelData),
    processingTypeDataProvider = mapProcessingTypeDataProvider(existingProcessingType -> ProcessingTypeData.createProcessingTypeData(processManagerProvider, processingTypeConfig)),
    subprocessRepository,
    typesForCategories)

  val processesRouteWithAllPermissions: Route = withAllPermissions(processesRoute)

  val settingsRouteWithoutPermissions: Route = withoutPermissions(settingsRoute)

  def deployRoute(requireComment: Boolean = false) = new ManagementResources(
    processCounter = new ProcessCounter(TestFactory.sampleSubprocessRepository),
    managementActor = managementActor,
    testResultsMaxSizeInBytes = 500 * 1024 * 1000,
    processAuthorizer = processAuthorizer,
    processRepository = processRepository,
    deploySettings = Some(DeploySettings(requireComment = requireComment)),
    processResolving = processResolving
  )
  val attachmentService = new ProcessAttachmentService(attachmentsPath, processActivityRepository)
  val processActivityRoute = new ProcessActivityResource(processActivityRepository, processRepository)
  val attachmentsRoute = new AttachmentResources(attachmentService, processRepository)

  def createProcessAndAssertSuccess(processName: ProcessName, processCategory: String, isSubprocess: Boolean): Assertion = {
    Post(s"/processes/${processName.value}/$processCategory?isSubprocess=$isSubprocess") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
    }
  }

  def saveProcess(processName: ProcessName, process: EspProcess)(testCode: => Assertion): Assertion = {
    Post(s"/processes/${processName.value}/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(processName, process)(testCode)
    }
  }

  def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Post(s"/processes/$processId/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def saveSubProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Post(s"/processes/$processId/$testCategoryName?isSubprocess=true") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def updateProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Put(s"/processes/$processId", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(process: ProcessToSave)(testCode: => Assertion): Assertion = {
    val processId = process.process.id
    Put(s"/processes/$processId", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(processName: ProcessName, process: EspProcess)(testCode: => Assertion): Assertion = {
    Put(s"/processes/${processName.value}", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def saveProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    saveProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }
  }

  def updateProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    updateProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }
  }

  def deployProcess(processName: String, requireComment: Boolean = false, comment: Option[String] = None): RouteTestResult = {
    Post(s"/processManagement/deploy/$processName",
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, comment.getOrElse(""))
    ) ~> withPermissions(deployRoute(requireComment), testPermissionDeploy |+| testPermissionRead)
  }

  def cancelProcess(id: String, requireComment: Boolean = false, comment: Option[String] = None): RouteTestResult = {
    Post(s"/processManagement/cancel/$id",
      HttpEntity(ContentTypes.`text/plain(UTF-8)`, comment.getOrElse(""))
    ) ~> withPermissions(deployRoute(requireComment), testPermissionDeploy |+| testPermissionRead)
  }

  def snapshot(processName: String): RouteTestResult = {
    Post(s"/adminProcessManagement/snapshot/$processName") ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)
  }

  def stop(processName: String): RouteTestResult = {
    Post(s"/adminProcessManagement/stop/$processName") ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)
  }

  def customAction(processName: String, reqPayload: CustomActionRequest): RouteTestResult = {
    Post(s"/processManagement/customAction/$processName", TestFactory.posting.toRequest(reqPayload)) ~>
      withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)
  }

  def getSampleProcess: RouteTestResult = {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, testPermissionRead)
  }

  def getProcess(processName: ProcessName): RouteTestResult = {
    Get(s"/processes/${processName.value}") ~> withPermissions(processesRoute, testPermissionRead)
  }

  def archiveProcess(processName: ProcessName): RouteTestResult = {
    Post(s"/archive/${processName.value}") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead)
  }

 def unArchiveProcess(processName: ProcessName): RouteTestResult = {
    Post(s"/unarchive/${processName.value}") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead)
  }

  def getProcesses: RouteTestResult = {
    Get(s"/processes") ~> withPermissions(processesRoute, testPermissionRead)
  }

  def getSettings: RouteTestResult = {
    Get(s"/settings") ~> settingsRouteWithoutPermissions
  }

  def getUser(isAdmin: Boolean): RouteTestResult = {
    Get("/user") ~> (if (isAdmin) withAdminPermissions(usersRoute) else withAllPermissions(usersRoute))
  }

  def getProcessDefinitionData(processingType: String, subprocessVersions: Json): RouteTestResult = {
    Post(s"/processDefinitionData/$processingType?isSubprocess=false", toEntity(subprocessVersions)) ~> withPermissions(definitionResources, testPermissionRead)
  }

  def getProcessDefinitionServices(): RouteTestResult = {
    Get("/processDefinitionData/services") ~> withPermissions(definitionResources, testPermissionRead)
  }

  def toEntity[T:Encoder](data: T): HttpEntity.Strict = toEntity(implicitly[Encoder[T]].apply(data))

  private def toEntity(json: Json) = {
    val jsonString = json.pretty(Printer.spaces2.copy(dropNullValues = true, preserveOrder = true))
    HttpEntity(ContentTypes.`application/json`, jsonString)
  }

  private def makeEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean) = {
    val emptyCanonical = newProcessPreparer.prepareEmptyProcess(processId, processingType, isSubprocess)
    GraphProcess(ProcessMarshaller.toJson(emptyCanonical).spaces2)
  }

  private def prepareProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[process.ProcessId] = {
    val emptyProcess = makeEmptyProcess(processName.value, TestProcessingTypes.Streaming, isSubprocess)

    for {
      _ <- writeProcessRepository.saveNewProcess(processName, category, emptyProcess, TestProcessingTypes.Streaming, isSubprocess)
      id <- processRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

  def prepareDeploy(id: process.ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsDeployed(id, 1, "stream", Some("Deploy comment"))

  def prepareCancel(id: process.ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsCancelled(id, 1, Some("Cancel comment"))

  def createProcess(processName: ProcessName): process.ProcessId =
    createProcess(processName, testCategoryName, false)

  def createProcess(processName: ProcessName, category: String, isSubprocess: Boolean): process.ProcessId =
    prepareProcess(processName, category, isSubprocess).futureValue

  def createArchivedProcess(processName: ProcessName, isSubprocess: Boolean): process.ProcessId = {
    (for {
      id <- prepareProcess(processName, testCategoryName, isSubprocess)
      _ <- writeProcessRepository.archive(id, isArchived = true)
      _ <- actionRepository.markProcessAsArchived(id, 1, Some("Archived process"))
    } yield id).futureValue
  }

  def createDeployedProcess(processName: ProcessName, category: String, isSubprocess: Boolean) : process.ProcessId = {
    (for {
      id <- prepareProcess(processName, category, isSubprocess)
      _ <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedProcess(processName: ProcessName, isSubprocess: Boolean): process.ProcessId =
    createDeployedProcess(processName, testCategoryName, isSubprocess)

  //TODO replace all processName: String to processName: ProcessName
  def createDeployedProcess(processName: ProcessName): process.ProcessId =
    createDeployedProcess(processName, testCategoryName, false)

  def createDeployedCanceledProcess(processName: ProcessName, category: String, isSubprocess: Boolean) : process.ProcessId = {
    (for {
      id <- prepareProcess(processName, category, isSubprocess)
      _ <- prepareDeploy(id)
      _ <-  prepareCancel(id)
    } yield id).futureValue
  }

  def createDeployedCanceledProcess(processName: ProcessName, isSubprocess: Boolean) : process.ProcessId =
    createDeployedCanceledProcess(processName, testCategoryName, isSubprocess)

  def cancelProcess(id: process.ProcessId): Assertion =
    prepareCancel(id).map(_ => ()).futureValue shouldBe (())

  def parseResponseToListJsonProcess(response: String): List[ProcessJson] =
    parser.decode[List[Json]](response).right.get.map(j => ProcessJson(j))

  //TODO: In future we should identify process by id..
  def findJsonProcess(response: String, processId: String = SampleProcess.process.id): Option[ProcessJson] =
    parseResponseToListJsonProcess(response)
      .find(item => item.name === processId)

  def decodeJsonProcess(response: String): ProcessJson =
    ProcessJson(parser.decode[Json](response).right.get)
}

object ProcessJson{
  def apply(process: Json): ProcessJson = {
    val lastAction = process.hcursor.downField("lastAction").as[Option[Json]].right.get

    new ProcessJson(
      process.hcursor.downField("id").as[String].right.get,
      process.hcursor.downField("name").as[String].right.get,
      process.hcursor.downField("processId").as[Long].right.get,
      lastAction.map(_.hcursor.downField("processVersionId").as[Long].right.get),
      lastAction.map(_.hcursor.downField("action").as[String].right.get),
      process.hcursor.downField("state").downField("status").downField("name").as[Option[String]].right.get,
      process.hcursor.downField("state").downField("icon").as[Option[String]].right.get.map(URI.create),
      process.hcursor.downField("state").downField("tooltip").as[Option[String]].right.get,
      process.hcursor.downField("state").downField("description").as[Option[String]].right.get,
      process.hcursor.downField("isArchived").as[Boolean].right.get
    )
  }
}

case class ProcessJson(id: String,
                       name: String,
                       processId: Long,
                       lastActionVersionId: Option[Long],
                       lastActionType: Option[String],
                       stateStatus: Option[String],
                       stateIcon: Option[URI],
                       stateTooltip: Option[String],
                       stateDescription: Option[String],
                       isArchived: Boolean) {

  def isDeployed: Boolean = lastActionType.contains(ProcessActionType.Deploy.toString)
  def isCanceled: Boolean = lastActionType.contains(ProcessActionType.Cancel.toString)
}
