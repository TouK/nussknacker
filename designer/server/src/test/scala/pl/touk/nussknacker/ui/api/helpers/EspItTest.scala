package pl.touk.nussknacker.ui.api.helpers

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import cats.instances.all._
import cats.syntax.semigroup._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.syntax._
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, BeforeAndAfterEach, OptionValues, Suite}
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, ValidatedProcessDetails}
import pl.touk.nussknacker.restmodel.{CustomActionRequest, processdetails}
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessesQuery
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.{DefaultProcessingTypeDeploymentService, MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider, ProcessingTypeDataReader}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.DbSubprocessRepository
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.{ConfigWithScalaVersion, MultipartUtils}
import slick.dbio.DBIOAction
import _root_.sttp.client3.SttpBackend
import _root_.sttp.client3.akkahttp.AkkaHttpBackend
import pl.touk.nussknacker.engine.definition.test.{ModelDataTestInfoProvider, TestInfoProvider}
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

trait EspItTest extends LazyLogging with WithHsqlDbTesting with TestPermissions { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers with ScalaFutures =>

  import ProcessesQueryEnrichments.RichProcessesQuery
  import TestCategories._
  import TestProcessingTypes._

  protected implicit val processCategoryService: ProcessCategoryService = new ConfigProcessCategoryService(testConfig)

  private implicit val sttpBackend: SttpBackend[Future, Any] = AkkaHttpBackend.usingActorSystem(system)

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  protected val dbioRunner: DBIOActionRunner = newDBIOActionRunner(db)

  protected val fetchingProcessRepository: DBFetchingProcessRepository[DB] = newFetchingProcessRepository(db)

  protected val futureFetchingProcessRepository: DBFetchingProcessRepository[Future] = newFutureFetchingProcessRepository(db)

  protected val processAuthorizer: AuthorizeProcess = new AuthorizeProcess(futureFetchingProcessRepository)

  protected val writeProcessRepository: DBProcessRepository = newWriteProcessRepository(db)

  protected val subprocessRepository: DbSubprocessRepository = newSubprocessRepository(db)

  protected val actionRepository: DbProcessActionRepository[DB] = newActionProcessRepository(db)

  protected val processActivityRepository: DbProcessActivityRepository = newProcessActivityRepository(db)

  protected val processChangeListener = new TestProcessChangeListener()

  protected lazy val deploymentManager: MockDeploymentManager = createDeploymentManager()

  protected val dmDispatcher = new DeploymentManagerDispatcher(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> deploymentManager),
    futureFetchingProcessRepository)

  protected implicit val deploymentService: DeploymentService =
    new DeploymentServiceImpl(dmDispatcher, fetchingProcessRepository, actionRepository, dbioRunner, processValidation, scenarioResolver, processChangeListener, None)

  private implicit val processingTypeDeploymentService: DefaultProcessingTypeDeploymentService =
    new DefaultProcessingTypeDeploymentService(Streaming, deploymentService)

  protected val processingTypeConfig: ProcessingTypeConfig =
    ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)

  protected val deploymentManagerProvider: FlinkStreamingDeploymentManagerProvider = new FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: BaseModelData, config: Config)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Any],
                                         deploymentService: ProcessingTypeDeploymentService): DeploymentManager = deploymentManager
  }

  protected val testModelDataProvider: ProcessingTypeDataProvider[ModelData, _] = mapProcessingTypeDataProvider(
    Streaming -> ModelData(processingTypeConfig)
  )

  protected val testProcessingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData, _] = mapProcessingTypeDataProvider(
    Streaming -> ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, processingTypeConfig)
  )

  protected val newProcessPreparer: NewProcessPreparer = createNewProcessPreparer()

  protected val featureTogglesConfig: FeatureTogglesConfig = FeatureTogglesConfig.create(testConfig)

  protected val typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData, _] =
    ProcessingTypeDataReader.loadProcessingTypeData(ConfigWithUnresolvedVersion(testConfig))

  protected val customActionInvokerService = new CustomActionInvokerServiceImpl(futureFetchingProcessRepository, dmDispatcher, deploymentService)

  protected val testExecutorService = new ScenarioTestExecutorServiceImpl(scenarioResolver, dmDispatcher)

  protected val processService: DBProcessService = createDBProcessService(deploymentService)

  protected val scenarioTestService: ScenarioTestService = createScenarioTestService(testModelDataProvider.mapValues(new ModelDataTestInfoProvider(_)))

  protected val configProcessToolbarService = new ConfigProcessToolbarService(testConfig, processCategoryService.getAllCategories)

  protected val processesRoute = new ProcessesResources(
    processRepository = futureFetchingProcessRepository,
    processService = processService,
    deploymentService = deploymentService,
    processToolbarService = configProcessToolbarService,
    processResolving = processResolving,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener
  )

  protected val processActivityRoute = new ProcessActivityResource(processActivityRepository, futureFetchingProcessRepository, processAuthorizer)

  protected val processActivityRouteWithAllPermissions: Route = withAllPermissions(processActivityRoute)

  protected val processesRouteWithAllPermissions: Route = withAllPermissions(processesRoute)

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  protected def createDBProcessService(deploymentService: DeploymentService): DBProcessService =
    new DBProcessService(deploymentService, newProcessPreparer,
      processCategoryService, processResolving, dbioRunner, futureFetchingProcessRepository,
      actionRepository, writeProcessRepository, processValidation
    )

  protected def createScenarioTestService(testInfoProviders: ProcessingTypeDataProvider[TestInfoProvider, _]): ScenarioTestService =
    new ScenarioTestService(testInfoProviders, featureTogglesConfig.testDataSettings, new PreliminaryScenarioTestDataSerDe(featureTogglesConfig.testDataSettings),
      processResolving, new ProcessCounter(TestFactory.prepareSampleSubprocessRepository), testExecutorService)

  protected def deployRoute(deploymentCommentSettings: Option[DeploymentCommentSettings] = None) = new ManagementResources(
    processAuthorizer = processAuthorizer,
    processRepository = futureFetchingProcessRepository,
    deploymentCommentSettings = deploymentCommentSettings,
    deploymentService = deploymentService,
    dispatcher = dmDispatcher,
    customActionInvokerService = customActionInvokerService,
    metricRegistry = new MetricRegistry,
    scenarioTestService = scenarioTestService,
  )

  protected def createDeploymentManager(): MockDeploymentManager = new MockDeploymentManager


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    processChangeListener.clear()
  }

  protected def saveProcessAndAssertSuccess(processId: String, process: CanonicalProcess, category: String = TestCat): Assertion =
    saveProcess(ProcessName(processId), process, category) {
      status shouldEqual StatusCodes.OK
    }

  protected def saveProcess(processName: ProcessName, process: CanonicalProcess, category: String)(testCode: => Assertion): Assertion =
    createProcessRequest(processName, category) { _ =>
      val json = parser.decode[Json](responseAs[String]).toOption.get
      val resp = CreateProcessResponse(json)

      resp.processName shouldBe processName

      updateProcess(processName, process)(testCode)
    }

  protected def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    createProcessRequest(ProcessName(process.id), process.category) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  protected def createProcessRequest(processName: ProcessName, category: String = TestCat)(callback: StatusCode => Assertion): Assertion =
    Post(s"/processes/${processName.value}/$category?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      callback(status)
    }

  protected def saveSubProcess(process: CanonicalProcess)(testCode: => Assertion): Assertion = {
    val displayable = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, TestCat)
    saveSubProcess(displayable)(testCode)
  }

  protected def saveSubProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    Post(s"/processes/${process.id}/${process.category}?isSubprocess=true") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  protected def updateProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion =
    Put(s"/processes/${process.id}", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def updateProcess(process: UpdateProcessCommand)(testCode: => Assertion): Assertion =
    Put(s"/processes/${process.process.id}", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def updateProcessAndAssertSuccess(processId: String, process: CanonicalProcess): Assertion =
    updateProcess(ProcessName(processId), process) {
      status shouldEqual StatusCodes.OK
    }

  protected def updateProcess(processName: ProcessName, process: CanonicalProcess, comment: String = "")(testCode: => Assertion): Assertion =
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

  protected def testScenario(scenario: CanonicalProcess, testDataContent: String): RouteTestResult = {
    val displayableProcess = ProcessConverter.toDisplayable(scenario, TestProcessingTypes.Streaming, Category1)
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> testDataContent, "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/processManagement/test/${scenario.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead)
  }

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
    implicit val basicProcessesUnmarshaller: FromEntityUnmarshaller[List[BasicProcess]] = FailFastCirceSupport.unmarshaller(implicitly[Decoder[List[BasicProcess]]])
    val url = query.createQueryParamsUrl("/processes")

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = parseResponseToListJsonProcess(responseAs[String])
      responseAs[List[BasicProcess]] // just to test if decoder succeds
      callback(processes)
    }
  }

  protected def forScenariosDetailsReturned(query: ProcessesQuery, isAdmin: Boolean = false)(callback: List[ValidatedProcessDetails] => Unit): Unit = {
    import FailFastCirceSupport._

    val url = query.createQueryParamsUrl("/processesDetails")

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = responseAs[List[ValidatedProcessDetails]]
      callback(processes)
    }
  }

  protected def routeWithPermissions(route: RouteWithUser, isAdmin: Boolean = false): Route =
    if (isAdmin) withAdminPermissions(route) else withAllPermissions(route)

  protected def toEntity[T:Encoder](data: T): HttpEntity.Strict = toEntity(implicitly[Encoder[T]].apply(data))

  private def toEntity(json: Json) = {
    val jsonString = json.printWith(humanReadablePrinter)
    HttpEntity(ContentTypes.`application/json`, jsonString)
  }

  protected def createProcess(process: CanonicalProcess, category: String, processingType: ProcessingType): ProcessId = {
    saveAndGetId(process, category, process.metaData.isSubprocess, processingType).futureValue
  }

  private def prepareValidProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[ProcessId] = {
    val validProcess: CanonicalProcess = if (isSubprocess) SampleFragment.fragment else SampleProcess.process
    val withNameSet = validProcess.copy(metaData = validProcess.metaData.copy(id = processName.value))
    saveAndGetId(withNameSet, category, isSubprocess)
  }

  private def prepareEmptyProcess(processName: ProcessName, category: String, isSubprocess: Boolean): Future[ProcessId] = {
    val emptyProcess = newProcessPreparer.prepareEmptyProcess(processName.value, Streaming, isSubprocess)
    saveAndGetId(emptyProcess, category, isSubprocess)
  }

  private def saveAndGetId(process: CanonicalProcess, category: String, isSubprocess: Boolean, processingType: ProcessingType = Streaming): Future[ProcessId] = {
    val processName = ProcessName(process.id)
    val action = CreateProcessAction(processName, category, process, processingType, isSubprocess, ScenarioComponentsUsages.Empty)
    for {
      _ <- dbioRunner.runInTransaction(writeProcessRepository.saveNewProcess(action))
      id <- futureFetchingProcessRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

  protected def getProcessDetails(processId: ProcessId): processdetails.BaseProcessDetails[Unit] =
    futureFetchingProcessRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).futureValue.get

  protected def prepareDeploy(id: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Deploy
    val comment = DeploymentComment.unsafe("Deploy comment").toComment(actionType)
    dbioRunner.run(actionRepository.addInstantAction(id, VersionId.initialVersionId, actionType, Some(comment), Some(Streaming)))
  }

  protected def prepareCancel(id: ProcessId): Future[_] = {
    val actionType = ProcessActionType.Cancel
    val comment = DeploymentComment.unsafe("Cancel comment").toComment(actionType)
    dbioRunner.run(actionRepository.addInstantAction(id, VersionId.initialVersionId, actionType, Some(comment), None))
  }

  protected def createEmptyProcess(processName: ProcessName, category: String = TestCat, isSubprocess: Boolean = false): ProcessId =
    prepareEmptyProcess(processName, category, isSubprocess).futureValue

  protected def createValidProcess(processName: ProcessName, category: String = TestCat, isSubprocess: Boolean = false): ProcessId =
    prepareValidProcess(processName, category, isSubprocess).futureValue

  protected def createArchivedProcess(processName: ProcessName, isSubprocess: Boolean = false): ProcessId = {
    (for {
      id <- prepareValidProcess(processName, TestCat, isSubprocess)
      _ <- dbioRunner.runInTransaction(DBIOAction.seq(
        writeProcessRepository.archive(processId = id, isArchived = true),
        actionRepository.markProcessAsArchived(processId = id, VersionId(1))
      ))
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
    parser.decode[List[Json]](response).toOption.get.map(j => ProcessJson(j))

  private def decodeJsonProcess(response: String): ProcessJson =
    ProcessJson(parser.decode[Json](response).toOption.get)
}

final case class ProcessVersionJson(id: Long)

object ProcessVersionJson {
  def apply(process: Json): ProcessVersionJson = ProcessVersionJson(
    process.hcursor.downField("processVersionId").as[Long].toOption.get
  )
}

object ProcessJson extends OptionValues {
  def apply(process: Json): ProcessJson = {
    val lastAction = process.hcursor.downField("lastAction").as[Option[Json]].toOption.value

    new ProcessJson(
      process.hcursor.downField("id").as[String].toOption.value,
      process.hcursor.downField("name").as[String].toOption.value,
      process.hcursor.downField("processId").as[Long].toOption.value,
      lastAction.map(_.hcursor.downField("processVersionId").as[Long].toOption.value),
      lastAction.map(_.hcursor.downField("action").as[String].toOption.value),
      process.hcursor.downField("state").downField("status").downField("name").as[String].toOption.value,
      process.hcursor.downField("state").downField("icon").as[String].toOption.map(URI.create).value,
      process.hcursor.downField("state").downField("tooltip").as[String].toOption.value,
      process.hcursor.downField("state").downField("description").as[String].toOption.value,
      process.hcursor.downField("processCategory").as[String].toOption.value,
      process.hcursor.downField("isArchived").as[Boolean].toOption.value,
      process.hcursor.downField("history").as[Option[List[Json]]].toOption.value.map(_.map(v => ProcessVersionJson(v)))
    )
  }
}

final case class ProcessJson(id: String,
                             name: String,
                             processId: Long,
                             lastActionVersionId: Option[Long],
                             lastActionType: Option[String],
                             stateStatus: String,
                             stateIcon: URI,
                             stateTooltip: String,
                             stateDescription: String,
                             processCategory: String,
                             isArchived: Boolean,
                             //Process on list doesn't contain history
                             history: Option[List[ProcessVersionJson]]) {

  def isDeployed: Boolean = lastActionType.contains(ProcessActionType.Deploy.toString)

  def isCanceled: Boolean = lastActionType.contains(ProcessActionType.Cancel.toString)
}

object CreateProcessResponse {
  def apply(data: Json): CreateProcessResponse = CreateProcessResponse(
    data.hcursor.downField("id").as[Long].map(ProcessId(_)).toOption.get,
    data.hcursor.downField("versionId").as[Long].map(VersionId(_)).toOption.get,
    data.hcursor.downField("processName").as[String].map(ProcessName(_)).toOption.get
  )
}

final case class CreateProcessResponse(id: ProcessId, versionId: VersionId, processName: ProcessName)

object ProcessesQueryEnrichments {

  implicit class RichProcessesQuery(query: ProcessesQuery) {

    def subprocess(): ProcessesQuery =
      query.copy(isSubprocess = Some(true))

    def unarchived(): ProcessesQuery =
      query.copy(isArchived = Some(false))

    def archived(): ProcessesQuery =
      query.copy(isArchived = Some(true))

    def deployed(): ProcessesQuery =
      query.copy(isDeployed = Some(true))

    def notDeployed(): ProcessesQuery =
      query.copy(isDeployed = Some(false))

    def names(names: List[String]): ProcessesQuery =
      query.copy(names = Some(names))

    def categories(categories: List[String]): ProcessesQuery =
      query.copy(categories = Some(categories))

    def processingTypes(processingTypes: List[String]): ProcessesQuery =
      query.copy(processingTypes = Some(processingTypes))

    def createQueryParamsUrl(path: String): String = {
      var url = s"$path?fake=true"

      query.isArchived.foreach { isArchived =>
        url += s"&isArchived=$isArchived"
      }

      query.isSubprocess.foreach { isSubprocess =>
        url += s"&isSubprocess=$isSubprocess"
      }

      query.isDeployed.foreach { isDeployed =>
        url += s"&isDeployed=$isDeployed"
      }

      query.categories.foreach { categories =>
        url += s"&categories=${categories.mkString(",")}"
      }

      query.processingTypes.foreach { processingTypes =>
        url += s"&processingTypes=${processingTypes.mkString(",")}"
      }

      query.names.foreach { names =>
        url += s"&names=${names.mkString(",")}"
      }

      url
    }
  }

}
