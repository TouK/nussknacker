package pl.touk.nussknacker.ui.api.helpers

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.all._
import cats.syntax.semigroup._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import io.circe.{Encoder, Json, parser}
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.{BaseModelData, ModelData, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.{CustomActionRequest, processdetails}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.db.entity.ProcessActionEntityData
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService, ManagementActor}
import pl.touk.nussknacker.ui.process.processingtypedata.{DefaultProcessingTypeDeploymentService, MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider, ProcessingTypeDataReader}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.DbSubprocessRepository
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import sttp.client.akkahttp.AkkaHttpBackend
import sttp.client.{NothingT, SttpBackend}

import java.net.URI
import java.time
import scala.concurrent.{ExecutionContext, Future}

trait EspItTest extends LazyLogging with WithHsqlDbTesting with TestPermissions { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers with ScalaFutures =>

  import TestProcessingTypes._
  import TestCategories._

  protected implicit val processCategoryService: ProcessCategoryService = new ConfigProcessCategoryService(testConfig)

  private implicit val sttpBackend: SttpBackend[Future, Nothing, NothingT] = AkkaHttpBackend.usingActorSystem(system)

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  protected val repositoryManager: RepositoryManager[DB] = newDBRepositoryManager(db)

  protected val fetchingProcessRepository: DBFetchingProcessRepository[Future] with BasicRepository = newFetchingProcessRepository(db)

  protected val processAuthorizer: AuthorizeProcess = new AuthorizeProcess(fetchingProcessRepository)

  protected val writeProcessRepository: DBProcessRepository = newWriteProcessRepository(db)

  protected val subprocessRepository: DbSubprocessRepository = newSubprocessRepository(db)

  protected val actionRepository: DbProcessActionRepository = newActionProcessRepository(db)

  protected val processActivityRepository: DbProcessActivityRepository = newProcessActivityRepository(db)

  protected val processChangeListener = new TestProcessChangeListener()

  private implicit val deploymentService: DeploymentService =
    new DeploymentService(fetchingProcessRepository, actionRepository, scenarioResolver, processChangeListener)

  private implicit val processingTypeDeploymentService: DefaultProcessingTypeDeploymentService =
    new DefaultProcessingTypeDeploymentService(Streaming, deploymentService)

  protected val processingTypeConfig: ProcessingTypeConfig =
    ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)

  protected val deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider = new FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                         deploymentService: ProcessingTypeDeploymentService): DeploymentManager = deploymentManager
  }

  protected val testModelDataProvider: MapBasedProcessingTypeDataProvider[ModelData] = mapProcessingTypeDataProvider(
    Streaming -> ModelData(processingTypeConfig)
  )

  protected val testProcessingTypeDataProvider: MapBasedProcessingTypeDataProvider[ProcessingTypeData] = mapProcessingTypeDataProvider(
    Streaming -> ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, processingTypeConfig)
  )

  protected lazy val deploymentManager: MockDeploymentManager = createDeploymentManager()

  protected val newProcessPreparer: NewProcessPreparer = createNewProcessPreparer()

  protected val featureTogglesConfig: FeatureTogglesConfig = FeatureTogglesConfig.create(testConfig)

  protected val typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData] = ProcessingTypeDataReader.loadProcessingTypeData(testConfig)

  protected val managementActor: ActorRef = createManagementActorRef

  protected val processService: DBProcessService = createDBProcessService(managementActor)

  protected val configProcessToolbarService = new ConfigProcessToolbarService(testConfig, processCategoryService.getAllCategories)

  protected val processesRoute = new ProcessesResources(
    processRepository = fetchingProcessRepository,
    subprocessRepository = subprocessRepository,
    processService = processService,
    processToolbarService = configProcessToolbarService,
    processResolving = processResolving,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener,
    typeToConfig = typeToConfig
  )

  protected val processActivityRoute = new ProcessActivityResource(processActivityRepository, fetchingProcessRepository, processAuthorizer)

  protected val processActivityRouteWithAllPermissions: Route = withAllPermissions(processActivityRoute)

  protected val processesRouteWithAllPermissions: Route = withAllPermissions(processesRoute)

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  protected def createManagementActorRef: ActorRef = system.actorOf(ManagementActor.props(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager),
    fetchingProcessRepository,
    TestFactory.scenarioResolver,
    deploymentService), "management")

  protected def createDBProcessService(managerActor: ActorRef): DBProcessService =
    new DBProcessService(managerActor, time.Duration.ofMinutes(1), newProcessPreparer,
      processCategoryService, processResolving, repositoryManager, fetchingProcessRepository,
      actionRepository, writeProcessRepository
    )

  protected def deployRoute(deploymentCommentSettings: Option[DeploymentCommentSettings] = None) = new ManagementResources(
    processCounter = new ProcessCounter(TestFactory.prepareSampleSubprocessRepository),
    managementActor = managementActor,
    processAuthorizer = processAuthorizer,
    processRepository = fetchingProcessRepository,
    deploymentCommentSettings = deploymentCommentSettings,
    processResolving = processResolving,
    processService = processService,
    testDataSettings = TestDataSettings(5, 1000, 100000),
    metricRegistry = new MetricRegistry
  )

  protected def createDeploymentManager(): MockDeploymentManager = new MockDeploymentManager

  protected def saveProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion =
    saveProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }

  protected def saveProcess(processName: ProcessName, process: EspProcess)(testCode: => Assertion): Assertion =
    createProcessRequest(processName) { _ =>
      val json = parser.decode[Json](responseAs[String]).right.get
      val resp = CreateProcessResponse(json)

      resp.processName shouldBe processName

      updateProcess(processName, process)(testCode)
    }

  protected def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion =
    createProcessRequest(ProcessName(process.id)) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }

  protected def createProcessRequest(processName: ProcessName)(callback: StatusCode => Assertion): Assertion =
    Post(s"/processes/${processName.value}/$TestCat?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      callback(status)
    }

  protected def saveSubProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion =
    Post(s"/processes/${process.id}/$TestCat?isSubprocess=true") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }

  protected def updateProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion =
    Put(s"/processes/${process.id}", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def updateProcess(process: UpdateProcessCommand)(testCode: => Assertion): Assertion =
    Put(s"/processes/${process.process.id}", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def updateProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion =
    updateProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }

  protected def updateProcess(processName: ProcessName, process: EspProcess, comment: String = "")(testCode: => Assertion): Assertion =
    Put(s"/processes/${processName.value}", TestFactory.posting.toEntityAsProcessToSave(process, comment)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def deployProcess(processName: String, deploymentCommentSettings: Option[DeploymentCommentSettings] = None, comment: Option[String] = None): RouteTestResult =
    Post(s"/processManagement/deploy/$processName", HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))) ~>
      withPermissions(deployRoute(deploymentCommentSettings), testPermissionDeploy |+| testPermissionRead)

  protected def cancelProcess(id: String, deploymentCommentSettings: Option[DeploymentCommentSettings] = None, comment: Option[String] = None): RouteTestResult =
    Post(s"/processManagement/cancel/$id", HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))) ~>
      withPermissions(deployRoute(deploymentCommentSettings), testPermissionDeploy |+| testPermissionRead)

  protected def snapshot(processName: String): RouteTestResult =
    Post(s"/adminProcessManagement/snapshot/$processName") ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)

  protected def stop(processName: String): RouteTestResult =
    Post(s"/adminProcessManagement/stop/$processName") ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)

  protected def customAction(processName: String, reqPayload: CustomActionRequest): RouteTestResult =
    Post(s"/processManagement/customAction/$processName", TestFactory.posting.toRequest(reqPayload)) ~>
      withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)

  protected def getProcesses: RouteTestResult =
    Get(s"/processes") ~> withPermissions(processesRoute, testPermissionRead)

  protected def getProcess(processName: ProcessName): RouteTestResult =
    Get(s"/processes/${processName.value}") ~> withPermissions(processesRoute, testPermissionRead)

  protected def getActivity(processName: ProcessName): RouteTestResult =
    Get(s"/processes/${processName.value}/activity") ~> processActivityRouteWithAllPermissions

  protected def forScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(callback: ProcessJson => Unit): Unit =
    tryForScenarioReturned(processName, isAdmin) { (status, response) =>
      status shouldEqual StatusCodes.OK
      val process = decodeJsonProcess(response)
      callback(process)
    }

  protected def tryForScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(callback: (StatusCode, String) => Unit): Unit =
    Get(s"/processes/${processName.value}") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      callback(status, responseAs[String])
    }

  protected def forScenariosReturned(query: ProcessesQuery, isAdmin: Boolean = false)(callback: List[ProcessJson] => Unit): Unit = {
    val url = ProcessesQuery.createQueryParamsUrl(query)

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = parseResponseToListJsonProcess(responseAs[String])
      callback(processes)
    }
  }

  object ProcessesQuery {
    def empty: ProcessesQuery =
      ProcessesQuery(List.empty, isArchived = None, isSubprocess = None, isDeployed = None)

    def categories(categories: List[String]): ProcessesQuery =
      ProcessesQuery(categories, isArchived = None, isSubprocess = None, isDeployed = None)

    def archived(categories: List[String] = List.empty, isSubprocess: Option[Boolean] = Some(false)): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = isSubprocess, isArchived = Some(true), isDeployed = None)

    def subprocess(categories: List[String ] = List.empty, isArchived: Option[Boolean] = Some(false)): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = Some(true), isArchived = isArchived, isDeployed = None)

    def deployed(categories: List[String ] = List.empty): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = Some(false), isArchived = Some(false), isDeployed = Some(true))

    def notDeployed(categories: List[String ] = List.empty): ProcessesQuery =
      ProcessesQuery(categories, isSubprocess = Some(false), isArchived = Some(false), isDeployed = Some(false))

    def createQueryParamsUrl(query: ProcessesQuery): String = {
      var url = s"/processes?fake=true"

      if (query.categories.nonEmpty) {
        url += s"&categories=${query.categories.mkString(",")}"
      }

      query.isArchived.foreach { isArchived =>
        url += s"&isArchived=$isArchived"
      }

      query.isSubprocess.foreach { isSubprocess =>
        url += s"&isSubprocess=$isSubprocess"
      }

      query.isDeployed.foreach { isDeployed =>
        url += s"&isDeployed=$isDeployed"
      }

      url
    }

  }

  case class ProcessesQuery(categories: List[String], isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean])

  protected def routeWithPermissions(route: RouteWithUser, isAdmin: Boolean = false): Route =
    if (isAdmin) withAdminPermissions(route) else withAllPermissions(route)

  protected def toEntity[T:Encoder](data: T): HttpEntity.Strict = toEntity(implicitly[Encoder[T]].apply(data))

  private def toEntity(json: Json) = {
    val jsonString = json.printWith(humanReadablePrinter)
    HttpEntity(ContentTypes.`application/json`, jsonString)
  }

  protected def createProcess(process: EspProcess, category: String, processingType: ProcessingType): ProcessId = {
    val cannonical = process.toCanonicalProcess
    saveAndGetId(cannonical, category, cannonical.metaData.isSubprocess, processingType).futureValue
  }

  private def prepareValidProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[ProcessId] = {
    val validProcess: CanonicalProcess = {
      if(isSubprocess) SampleFragment.fragment
      else SampleProcess.process.toCanonicalProcess
    }
    val withNameSet = validProcess.copy(metaData = validProcess.metaData.copy(id = processName.value))

    saveAndGetId(withNameSet, category, isSubprocess)
  }

  private def prepareEmptyProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[ProcessId] = {
    val emptyProcess = newProcessPreparer.prepareEmptyProcess(processName.value, Streaming, isSubprocess)
    saveAndGetId(emptyProcess, category, isSubprocess)
  }

  private def saveAndGetId(process: CanonicalProcess, category: String, isSubprocess: Boolean, processingType: ProcessingType = Streaming): Future[ProcessId] = {
    val processName = ProcessName(process.id)
    val action = CreateProcessAction(processName, category, process, processingType, isSubprocess)
    for {
      _ <- repositoryManager.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- fetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

  protected def getProcessDetails(processId: ProcessId): processdetails.BaseProcessDetails[Unit] =
    fetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).futureValue.get

  protected def prepareDeploy(id: ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsDeployed(id, VersionId.initialVersionId, Streaming, Some(DeploymentComment.unsafe("Deploy comment")))

  protected def prepareCancel(id: ProcessId): Future[ProcessActionEntityData] =
    actionRepository.markProcessAsCancelled(id, VersionId.initialVersionId, Some(DeploymentComment.unsafe("Cancel comment")))

  protected def createEmptyProcess(processName: ProcessName, category: String = TestCat, isSubprocess: Boolean = false): ProcessId =
    prepareEmptyProcess(processName, category, isSubprocess).futureValue

  protected def createValidProcess(processName: ProcessName, category: String = TestCat, isSubprocess: Boolean = false): ProcessId =
    prepareValidProcess(processName, category, isSubprocess).futureValue

  protected def createArchivedProcess(processName: ProcessName, isSubprocess: Boolean = false): ProcessId = {
    (for {
      id <- prepareValidProcess(processName, TestCat, isSubprocess)
      _ <- repositoryManager.runInTransaction(
        writeProcessRepository.archive(processId = id, isArchived = true),
        actionRepository.markProcessAsArchived(processId = id, VersionId(1))
      )
    } yield id).futureValue
  }

  protected def createDeployedProcess(processName: ProcessName, category: String = TestCat) : ProcessId = {
    (for {
      id <- prepareValidProcess(processName, category, isSubprocess = false)
      _ <- prepareDeploy(id)
    } yield id).futureValue
  }

  protected def createDeployedCanceledProcess(processName: ProcessName, category: String = TestCat) : ProcessId = {
    (for {
      id <- prepareValidProcess(processName, category, isSubprocess = false)
      _ <- prepareDeploy(id)
      _ <-  prepareCancel(id)
    } yield id).futureValue
  }

  protected def parseResponseToListJsonProcess(response: String): List[ProcessJson] =
    parser.decode[List[Json]](response).right.get.map(j => ProcessJson(j))

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
