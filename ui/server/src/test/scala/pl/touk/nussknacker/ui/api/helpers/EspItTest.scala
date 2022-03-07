package pl.touk.nussknacker.ui.api.helpers

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.all._
import cats.syntax.semigroup._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Encoder, Json, Printer, parser}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.{BaseModelData, ModelData, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.{CustomActionRequest, processdetails}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService, ManagementActor}
import pl.touk.nussknacker.ui.process.processingtypedata.{DefaultProcessingTypeDeploymentService, MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider, ProcessingTypeDataReader}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.basicauth.BasicAuthenticationConfiguration
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.client.{NothingT, SttpBackend}

import java.net.URI
import java.time
import scala.concurrent.{ExecutionContext, Future}

trait EspItTest extends LazyLogging with WithHsqlDbTesting with TestPermissions { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers with ScalaFutures =>

  implicit val sttpBackend: SttpBackend[Future, Nothing, NothingT] = AkkaHttpBackend.usingActorSystem(system)

  override def testConfig: Config = ConfigWithScalaVersion.config

  val env = "test"
  val attachmentsPath = "/tmp/attachments" + System.currentTimeMillis()

  val repositoryManager = newDBRepositoryManager(db)
  val fetchingProcessRepository = newFetchingProcessRepository(db)
  val processAuthorizer = new AuthorizeProcess(fetchingProcessRepository)

  val writeProcessRepository = newWriteProcessRepository(db)
  val subprocessRepository = newSubprocessRepository(db)
  val actionRepository = newActionProcessRepository(db)
  val processActivityRepository = newProcessActivityRepository(db)

  private implicit val deploymentService = new DeploymentService(fetchingProcessRepository, actionRepository, scenarioResolver)

  private implicit val processingTypeDeploymentService: DefaultProcessingTypeDeploymentService =
    new DefaultProcessingTypeDeploymentService(ConfigWithScalaVersion.streamingProcessingType, deploymentService)

  protected val processingTypeConfig: ProcessingTypeConfig = ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig)

  protected val deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider = new FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                         deploymentService: ProcessingTypeDeploymentService): DeploymentManager = deploymentManager
  }

  protected val testModelDataProvider: MapBasedProcessingTypeDataProvider[ModelData] = mapProcessingTypeDataProvider(
    TestProcessingTypes.Streaming -> ModelData(processingTypeConfig)
  )

  protected val testProcessingTypeDataProvider: MapBasedProcessingTypeDataProvider[ProcessingTypeData] = mapProcessingTypeDataProvider(
    TestProcessingTypes.Streaming -> ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, processingTypeConfig)
  )

  protected implicit val processCategoryService: ProcessCategoryService = new ConfigProcessCategoryService(testConfig)

  protected def createDeploymentManager(): MockDeploymentManager = new MockDeploymentManager

  lazy val deploymentManager: MockDeploymentManager = createDeploymentManager()

  val processChangeListener = new TestProcessChangeListener()

  def createManagementActorRef: ActorRef = {
    system.actorOf(ManagementActor.props(
      mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager),
      fetchingProcessRepository,
      actionRepository,
      TestFactory.scenarioResolver,
      processChangeListener,
      deploymentService), "management")
  }

  val managementActor: ActorRef = createManagementActorRef
  val newProcessPreparer: NewProcessPreparer = createNewProcessPreparer()
  val featureTogglesConfig: FeatureTogglesConfig = FeatureTogglesConfig.create(testConfig)
  val typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData] = ProcessingTypeDataReader.loadProcessingTypeData(testConfig)

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  val processService: DBProcessService = createDBProcessService(managementActor)
  val configProcessToolbarService = new ConfigProcessToolbarService(testConfig, processCategoryService.getAllCategories)

  val processesRoute = new ProcessesResources(
    processRepository = fetchingProcessRepository,
    processService = processService,
    processToolbarService = configProcessToolbarService,
    processValidation = processValidation,
    processResolving = processResolving,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener,
    typeToConfig = typeToConfig
  )

  val authenticationConfig = BasicAuthenticationConfiguration.create(testConfig)
  val analyticsConfig = AnalyticsConfig(testConfig)

  val usersRoute = new UserResources(processCategoryService)
  val settingsRoute = new SettingsResources(featureTogglesConfig, authenticationConfig.name, analyticsConfig)

  val processesExportResources = new ProcessesExportResources(fetchingProcessRepository, processActivityRepository, processResolving)

  val definitionResources = new DefinitionResources(
    modelDataProvider = testModelDataProvider,
    processingTypeDataProvider = testProcessingTypeDataProvider,
    subprocessRepository,
    processCategoryService
  )

  val processesRouteWithAllPermissions: Route = withAllPermissions(processesRoute)

  val settingsRouteWithoutPermissions: Route = withoutPermissions(settingsRoute)

  protected def createDBProcessService(managerActor: ActorRef): DBProcessService =
    new DBProcessService(
      managerActor, time.Duration.ofMinutes(1), newProcessPreparer, processCategoryService, processResolving,
      repositoryManager, fetchingProcessRepository, actionRepository, writeProcessRepository
    )

  def deployRoute(requireComment: Boolean = false) = new ManagementResources(
    processCounter = new ProcessCounter(TestFactory.prepareSampleSubprocessRepository),
    managementActor = managementActor,
    processAuthorizer = processAuthorizer,
    processRepository = fetchingProcessRepository,
    deploySettings = Some(DeploySettings(requireComment = requireComment)),
    processResolving = processResolving,
    processService = processService,
    testDataSettings = TestDataSettings(5, 1000, 100000)
  )
  val attachmentService = new ProcessAttachmentService(attachmentsPath, processActivityRepository)
  val processActivityRoute = new ProcessActivityResource(processActivityRepository, fetchingProcessRepository, processAuthorizer)
  val processActivityRouteWithAllPermissions: Route = withAllPermissions(processActivityRoute)
  val attachmentsRoute = new AttachmentResources(attachmentService, fetchingProcessRepository, processAuthorizer)
  val attachmentsRouteWithAllPermissions: Route = withAllPermissions(attachmentsRoute)

  def createProcessRequest(processName: ProcessName)(callback: StatusCode => Assertion): Assertion = {
    Post(s"/processes/${processName.value}/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      callback(status)
    }
  }

  def saveProcess(processName: ProcessName, process: EspProcess)(testCode: => Assertion): Assertion = {
    createProcessRequest(processName) { _ =>
      val json = parser.decode[Json](responseAs[String]).right.get
      val resp = CreateProcessResponse(json)

      resp.processName shouldBe processName

      updateProcess(processName, process)(testCode)
    }
  }

  def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    createProcessRequest(ProcessName(process.id)) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def saveProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    saveProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
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

  def updateProcess(process: UpdateProcessCommand)(testCode: => Assertion): Assertion = {
    val processId = process.process.id
    Put(s"/processes/$processId", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(processName: ProcessName, process: EspProcess, comment: String = "")(testCode: => Assertion): Assertion = {
    Put(s"/processes/${processName.value}", TestFactory.posting.toEntityAsProcessToSave(process, comment)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    updateProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }
  }

  def deployProcess(processName: String, requireComment: Boolean = false, comment: Option[String] = None): RouteTestResult = {
    Post(s"/processManagement/deploy/$processName", HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))) ~>
      withPermissions(deployRoute(requireComment), testPermissionDeploy |+| testPermissionRead)
  }

  def cancelProcess(id: String, requireComment: Boolean = false, comment: Option[String] = None): RouteTestResult = {
    Post(s"/processManagement/cancel/$id", HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))) ~>
      withPermissions(deployRoute(requireComment), testPermissionDeploy |+| testPermissionRead)
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

  def tryProcess(processName: ProcessName, isAdmin: Boolean = false)(callback: (StatusCode, String) => Unit): Unit =
    Get(s"/processes/${processName.value}") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      callback(status, responseAs[String])
    }

  def withProcess(processName: ProcessName, isAdmin: Boolean = false)(callback: ProcessJson => Unit): Unit = {
    tryProcess(processName, isAdmin) { (status, response) =>
      status shouldEqual StatusCodes.OK
      val process = decodeJsonProcess(response)
      callback(process)
    }
  }

  def getActivity(processName: ProcessName): RouteTestResult = {
    Get(s"/processes/${processName.value}/activity") ~> processActivityRouteWithAllPermissions
  }

  object ProcessesQuery {
    def empty: ProcessesQuery =
      ProcessesQuery(List.empty, isArchived = None, isSubprocess = None)

    def categories(categories: List[String]): ProcessesQuery =
      ProcessesQuery(categories, isArchived = None, isSubprocess = None)

    def archived(categories: List[String] = List.empty, isSubprocess: Option[Boolean] = Some(false)): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = isSubprocess, isArchived = Some(true))

    def subprocess(categories: List[String ] = List.empty, isArchived: Option[Boolean] = Some(false)): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = Some(true), isArchived = isArchived)

    def createQueryParamsUrl(query: ProcessesQuery): String = {
      var url = s"/processes?fake=true"

      if (query.categories.nonEmpty) {
        url += s"&categories=${query.categories.mkString(",")}"
      }

      if (query.isArchived.isDefined) {
        url += s"&isArchived=${query.isArchived.get.toString}"
      }

      if (query.isSubprocess.isDefined) {
        url += s"&isSubprocess=${query.isSubprocess.get.toString}"
      }

      url
    }
  }

  case class ProcessesQuery(categories: List[String], isSubprocess: Option[Boolean], isArchived: Option[Boolean])

  protected def withProcesses(query: ProcessesQuery, isAdmin: Boolean = false)(callback: List[ProcessJson] => Unit): Unit = {
    val url = ProcessesQuery.createQueryParamsUrl(query)

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = parseResponseToListJsonProcess(responseAs[String])
      callback(processes)
    }
  }

  protected def routeWithPermissions(route: RouteWithUser, isAdmin: Boolean = false): Route =
    if (isAdmin) withAdminPermissions(route) else withAllPermissions(route)

  def getProcesses: RouteTestResult = {
    Get(s"/processes") ~> withPermissions(processesRoute, testPermissionRead)
  }

  def getSettings: RouteTestResult = {
    Get(s"/settings") ~> settingsRouteWithoutPermissions
  }

  def getUser(isAdmin: Boolean): RouteTestResult =
    Get("/user") ~> routeWithPermissions(usersRoute, isAdmin)

  def getProcessDefinitionData(processingType: String): RouteTestResult = {
    Get(s"/processDefinitionData/$processingType?isSubprocess=false") ~> withPermissions(definitionResources, testPermissionRead)
  }

  def getProcessDefinitionServices: RouteTestResult = {
    Get("/processDefinitionData/services") ~> withPermissions(definitionResources, testPermissionRead)
  }

  def toEntity[T:Encoder](data: T): HttpEntity.Strict = toEntity(implicitly[Encoder[T]].apply(data))

  private def toEntity(json: Json) = {
    val jsonString = json.printWith(humanReadablePrinter)
    HttpEntity(ContentTypes.`application/json`, jsonString)
  }

  private def makeEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean) = {
    newProcessPreparer.prepareEmptyProcess(processId, processingType, isSubprocess)
  }

  protected def createProcess(process: EspProcess, category: String, processingType: ProcessingType): ProcessId = {
    val processName = ProcessName(process.metaData.id)
    val action = CreateProcessAction(processName, category, process.toCanonicalProcess, processingType, process.metaData.isSubprocess)

    (for {
      _ <- repositoryManager.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- fetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id).futureValue
  }

  private def prepareProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[ProcessId] = {
    val emptyProcess = newProcessPreparer.prepareEmptyProcess(processName.value, TestProcessingTypes.Streaming, isSubprocess)
    val action = CreateProcessAction(processName, category, emptyProcess, TestProcessingTypes.Streaming, isSubprocess)

    for {
      _ <- repositoryManager.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- fetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

  def getProcessDetails(processId: ProcessId): processdetails.BaseProcessDetails[Unit] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).futureValue.get

  def prepareDeploy(id: ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsDeployed(id, VersionId.initialVersionId, "stream", Some("Deploy comment"))

  def prepareCancel(id: ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsCancelled(id, VersionId.initialVersionId, Some("Cancel comment"))

  def createProcess(processName: ProcessName, isSubprocess: Boolean = false): ProcessId =
    createProcess(processName, testCategoryName, isSubprocess)

  def createProcess(processName: ProcessName, category: String, isSubprocess: Boolean): ProcessId =
    prepareProcess(processName, category, isSubprocess).futureValue

  def createArchivedProcess(processName: ProcessName, isSubprocess: Boolean = false): ProcessId = {
    (for {
      id <- prepareProcess(processName, testCategoryName, isSubprocess)
      _ <- repositoryManager.runInTransaction(
        writeProcessRepository.archive(processId = id, isArchived = true),
        actionRepository.markProcessAsArchived(processId = id, VersionId(1))
      )
    } yield id).futureValue
  }

  def createDeployedProcess(processName: ProcessName, category: String, isSubprocess: Boolean) : ProcessId = {
    (for {
      id <- prepareProcess(processName, category, isSubprocess)
      _ <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedProcess(processName: ProcessName, isSubprocess: Boolean = false): ProcessId =
    createDeployedProcess(processName, testCategoryName, isSubprocess)

  //TODO replace all processName: String to processName: ProcessName
  def createDeployedProcess(processName: ProcessName): ProcessId =
    createDeployedProcess(processName, testCategoryName, false)

  def createDeployedCanceledProcess(processName: ProcessName, category: String, isSubprocess: Boolean) : ProcessId = {
    (for {
      id <- prepareProcess(processName, category, isSubprocess)
      _ <- prepareDeploy(id)
      _ <-  prepareCancel(id)
    } yield id).futureValue
  }

  def createDeployedCanceledProcess(processName: ProcessName, isSubprocess: Boolean = false) : ProcessId =
    createDeployedCanceledProcess(processName, testCategoryName, isSubprocess)

  def parseResponseToListJsonProcess(response: String): List[ProcessJson] =
    parser.decode[List[Json]](response).right.get.map(j => ProcessJson(j))

  //TODO: In future we should identify process by id..
  def findJsonProcess(response: String, processId: String = SampleProcess.process.id): Option[ProcessJson] =
    parseResponseToListJsonProcess(response)
      .find(item => item.name === processId)

  private def decodeJsonProcess(response: String): ProcessJson =
    ProcessJson(parser.decode[Json](response).right.get)
}

final case class ProcessVersionJson(id: Long)

object ProcessVersionJson {
  def apply(process: Json): ProcessVersionJson = ProcessVersionJson(
    process.hcursor.downField("processVersionId").as[Long].right.get
  )
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
      process.hcursor.downField("processCategory").as[String].right.get,
      process.hcursor.downField("isArchived").as[Boolean].right.get,
      process.hcursor.downField("history").as[Option[List[Json]]].right.get.map(_.map(v => ProcessVersionJson(v)))
    )
  }
}

final case class ProcessJson(id: String,
                             name: String,
                             processId: Long,
                             lastActionVersionId: Option[Long],
                             lastActionType: Option[String],
                             stateStatus: Option[String],
                             stateIcon: Option[URI],
                             stateTooltip: Option[String],
                             stateDescription: Option[String],
                             processCategory: String,
                             isArchived: Boolean,
                             //Process on list doesn't contain history
                             history: Option[List[ProcessVersionJson]]) {

  def isDeployed: Boolean = lastActionType.contains(ProcessActionType.Deploy.toString)

  def isCanceled: Boolean = lastActionType.contains(ProcessActionType.Cancel.toString)
}

object CreateProcessResponse {
  def apply(data: Json): CreateProcessResponse = CreateProcessResponse(
    data.hcursor.downField("id").as[Long].map(ProcessId(_)).right.get,
    data.hcursor.downField("versionId").as[Long].map(VersionId(_)).right.get,
    data.hcursor.downField("processName").as[String].map(ProcessName(_)).right.get
  )
}

final case class CreateProcessResponse(id: ProcessId, versionId: VersionId, processName: ProcessName)
