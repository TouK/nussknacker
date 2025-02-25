package pl.touk.nussknacker.test.base.it

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCode, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances.DB
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{parser, Decoder, Encoder, Json}
import io.circe.syntax._
import io.dropwizard.metrics5.MetricRegistry
import org.scalatest.{Assertion, BeforeAndAfterEach, OptionValues, Suite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.process.VersionId.initialVersionId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.{ModelDataTestInfoProvider, TestInfoProvider}
import pl.touk.nussknacker.restmodel.{CancelRequest, DeployRequest}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.ProcessesQueryEnrichments.RichProcessesQuery
import pl.touk.nussknacker.test.config.{ConfigWithScalaVersion, WithSimplifiedDesignerConfig}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.{TestCategory, TestProcessingType}
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory.Category1
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{
  MockDeploymentManager,
  MockManagerProvider,
  TestProcessChangeListener,
  WithTestDeploymentManagerClassLoader
}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.scalas.AkkaHttpExtensions.toRequestEntity
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.{DesignerConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.ProcessService.{CreateScenarioCommand, UpdateScenarioCommand}
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.DefaultFragmentRepository
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypesConfigBasedProcessingTypeDataLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}
import pl.touk.nussknacker.ui.util.{MultipartUtils, NuPathMatchers}
import slick.dbio.DBIOAction

import java.net.URI
import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

// TODO: Consider using NuItTest with NuScenarioConfigurationHelper instead. This one will be removed in the future.
trait NuResourcesTest
    extends WithHsqlDbTesting
    with WithClock
    with WithSimplifiedDesignerConfig
    with WithSimplifiedConfigScenarioHelper
    with WithTestDeploymentManagerClassLoader
    with EitherValuesDetailedMessage
    with OptionValues
    with BeforeAndAfterEach
    with LazyLogging {
  self: ScalatestRouteTest with Suite with Matchers with ScalaFutures =>

  protected val adminUser: LoggedUser = TestFactory.adminUser("user")

  private implicit val implicitAdminUser: LoggedUser = adminUser

  protected val dbioRunner: DBIOActionRunner = newDBIOActionRunner(testDbRef)

  protected val fetchingProcessRepository: DBFetchingProcessRepository[DB] = newFetchingProcessRepository(testDbRef)

  protected val futureFetchingScenarioRepository: FetchingProcessRepository[Future] =
    newFutureFetchingScenarioRepository(
      testDbRef
    )

  protected val processAuthorizer: AuthorizeProcess = new AuthorizeProcess(futureFetchingScenarioRepository)

  protected val writeProcessRepository: DBProcessRepository = newWriteProcessRepository(testDbRef, clock)

  protected val fragmentRepository: DefaultFragmentRepository = newFragmentRepository(testDbRef)

  protected val actionRepository: ScenarioActionRepository = newActionProcessRepository(testDbRef)

  protected val scenarioActivityRepository: ScenarioActivityRepository = newScenarioActivityRepository(testDbRef, clock)

  protected val processChangeListener = new TestProcessChangeListener()

  protected lazy val deploymentManager: MockDeploymentManager = MockDeploymentManager.create()

  protected val deploymentCommentSettings: Option[DeploymentCommentSettings] = None

  protected val dmDispatcher = new DeploymentManagerDispatcher(
    mapProcessingTypeDataProvider(Streaming.stringify -> deploymentManager),
    futureFetchingScenarioRepository
  )

  protected val deploymentsStatusesProvider =
    new EngineSideDeploymentStatusesProvider(dmDispatcher, None)

  protected val scenarioStatusProvider: ScenarioStatusProvider = new ScenarioStatusProvider(
    deploymentsStatusesProvider,
    dmDispatcher,
    fetchingProcessRepository,
    actionRepository,
    dbioRunner,
  )

  protected val scenarioStatusPresenter = new ScenarioStatusPresenter(dmDispatcher)

  protected val actionService: ActionService = new ActionService(
    fetchingProcessRepository,
    actionRepository,
    dbioRunner,
    processChangeListener,
    scenarioStatusProvider,
    deploymentCommentSettings,
    Clock.systemUTC()
  )

  protected val deploymentService: DeploymentService =
    new DeploymentService(
      dmDispatcher,
      processValidatorByProcessingType,
      scenarioResolverByProcessingType,
      actionService,
      mapProcessingTypeDataProvider(),
    )

  protected val processingTypeConfig: ProcessingTypeConfig =
    ProcessingTypeConfig.read(ConfigWithScalaVersion.StreamingProcessTypeConfig)

  protected val deploymentManagerProvider: DeploymentManagerProvider = new MockManagerProvider(deploymentManager)

  private val modelClassLoaderProvider = ModelClassLoaderProvider(
    Map(Streaming.stringify -> ModelClassLoaderDependencies(processingTypeConfig.classPath, None)),
    deploymentManagersClassLoader
  )

  private val modelData =
    ModelData(
      processingTypeConfig,
      modelDependencies,
      modelClassLoaderProvider.forProcessingTypeUnsafe(Streaming.stringify)
    )

  protected val testProcessingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData, _] =
    mapProcessingTypeDataProvider(
      Streaming.stringify -> ProcessingTypeData.createProcessingTypeData(
        Streaming.stringify,
        modelData,
        deploymentManagerProvider,
        SchedulingForProcessingType.NotAvailable,
        deploymentManagerDependencies,
        deploymentManagersClassLoader,
        deploymentManagerProvider.defaultEngineSetupName,
        processingTypeConfig.deploymentConfig,
        processingTypeConfig.category,
        modelDependencies.componentDefinitionExtractionMode
      )
    )

  protected val featureTogglesConfig: FeatureTogglesConfig = FeatureTogglesConfig.create(testConfig)

  protected val typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData] = {
    val designerConfig = DesignerConfig.from(testConfig)
    ProcessingTypeDataProvider(
      new ProcessingTypesConfigBasedProcessingTypeDataLoader(() => IO.pure(designerConfig.processingTypeConfigs))
        .loadProcessingTypeData(
          _ => modelDependencies,
          _ => deploymentManagerDependencies,
          deploymentManagersClassLoader,
          modelClassLoaderProvider,
          Some(testDbRef),
        )
        .unsafeRunSync()
    )
  }

  protected val processService: DBProcessService = createDBProcessService(scenarioStatusProvider)

  protected val scenarioTestServiceByProcessingType: ProcessingTypeDataProvider[ScenarioTestService, _] =
    mapProcessingTypeDataProvider(
      Streaming.stringify -> createScenarioTestService(modelData)
    )

  protected val configProcessToolbarService =
    new ConfigScenarioToolbarService(CategoriesScenarioToolbarsConfigParser.parse(testConfig))

  protected val processesRoute = new ProcessesResources(
    processService = processService,
    scenarioStatusProvider = scenarioStatusProvider,
    scenarioStatusPresenter = scenarioStatusPresenter,
    processToolbarService = configProcessToolbarService,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener
  )

  protected val processActivityRoute =
    new TestResource.ProcessActivityResource(scenarioActivityRepository, processService, processAuthorizer, dbioRunner)

  protected val processActivityRouteWithAllPermissions: Route = withAllPermissions(processActivityRoute)

  protected val processesRouteWithAllPermissions: Route = withAllPermissions(processesRoute)

  override def testConfig: Config = ConfigWithScalaVersion.TestsConfig

  protected def createLoggedUser(id: String, name: String, permissions: Permission.Permission*): LoggedUser = {
    RealLoggedUser(id, name, Map(Category1.stringify -> permissions.toSet))
  }

  protected def createDBProcessService(processStateProvider: ScenarioStatusProvider): DBProcessService =
    new DBProcessService(
      processStateProvider,
      scenarioStatusPresenter,
      newProcessPreparerByProcessingType,
      typeToConfig.mapCombined(_.parametersService),
      processResolverByProcessingType,
      dbioRunner,
      futureFetchingScenarioRepository,
      actionRepository,
      writeProcessRepository,
    )

  protected def createScenarioTestService(modelData: ModelData): ScenarioTestService =
    createScenarioTestService(new ModelDataTestInfoProvider(modelData))

  protected def createScenarioTestService(
      testInfoProvider: TestInfoProvider
  ): ScenarioTestService =
    new ScenarioTestService(
      testInfoProvider,
      processResolver,
      featureTogglesConfig.testDataSettings,
      new PreliminaryScenarioTestDataSerDe(featureTogglesConfig.testDataSettings),
      new ProcessCounter(TestFactory.prepareSampleFragmentRepository),
      new ScenarioTestExecutorServiceImpl(
        new ScenarioResolver(sampleResolver, Streaming.stringify),
        deploymentManager
      )
    )

  protected def deployRoute() =
    new ManagementResources(
      processAuthorizer = processAuthorizer,
      processService = processService,
      deploymentService = deploymentService,
      dispatcher = dmDispatcher,
      metricRegistry = new MetricRegistry,
      scenarioTestServices = scenarioTestServiceByProcessingType,
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    processChangeListener.clear()
  }

  protected def saveCanonicalProcessAndAssertSuccess(
      process: CanonicalProcess
  ): Assertion =
    saveCanonicalProcess(process) {
      status shouldEqual StatusCodes.OK
    }

  protected def saveCanonicalProcess(process: CanonicalProcess)(
      testCode: => Assertion
  ): Assertion =
    createProcessRequest(process.name) { code =>
      code shouldBe StatusCodes.Created
      val json = parser.decode[Json](responseAs[String]).rightValue
      val resp = CreateProcessResponse(json)

      resp.processName shouldBe process.name

      updateCanonicalProcess(process)(testCode)
    }

  protected def saveProcess(
      scenarioGraph: ScenarioGraph
  )(testCode: => Assertion): Assertion = {
    val processName = ProcessTestData.sampleProcessName
    createProcessRequest(processName) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(scenarioGraph, processName)(testCode)
    }
  }

  protected def createProcessRequest(processName: ProcessName)(
      callback: StatusCode => Assertion
  ): Assertion = {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post("/processes", command.toJsonRequestEntity()) ~> processesRouteWithAllPermissions ~> check {
      callback(status)
    }
  }

  protected def saveFragment(
      scenarioGraph: ScenarioGraph,
      name: ProcessName = ProcessTestData.sampleFragmentName,
      category: String = Category1.stringify
  )(testCode: => Assertion): Assertion = {
    val command = CreateScenarioCommand(
      name,
      Some(category),
      processingMode = None,
      engineSetupName = None,
      isFragment = true,
      forwardedUserName = None
    )
    Post("/processes", command.toJsonRequestEntity()) ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(scenarioGraph, name)(testCode)
    }
  }

  protected def updateProcess(process: ScenarioGraph, name: ProcessName = ProcessTestData.sampleProcessName)(
      testCode: => Assertion
  ): Assertion =
    doUpdateProcess(UpdateScenarioCommand(process, None, Some(List.empty), None), name)(testCode)

  protected def updateCanonicalProcessAndAssertSuccess(process: CanonicalProcess): Assertion =
    updateCanonicalProcess(process) {
      status shouldEqual StatusCodes.OK
    }

  protected def updateCanonicalProcess(process: CanonicalProcess, comment: Option[String] = None)(
      testCode: => Assertion
  ): Assertion =
    doUpdateProcess(
      UpdateScenarioCommand(
        CanonicalProcessConverter.toScenarioGraph(process),
        comment,
        Some(List.empty),
        None
      ),
      process.name
    )(
      testCode
    )

  protected def doUpdateProcess(command: UpdateScenarioCommand, name: ProcessName = ProcessTestData.sampleProcessName)(
      testCode: => Assertion
  ): Assertion =
    Put(s"/processes/$name", command.toJsonRequestEntity()) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }

  protected def deployProcess(
      processName: ProcessName,
      comment: Option[String] = None
  ): RouteTestResult =
    Post(
      s"/processManagement/deploy/$processName",
      HttpEntity(
        ContentTypes.`application/json`,
        DeployRequest(comment, None).asJson.noSpaces
      )
    ) ~>
      withPermissions(deployRoute(), Permission.Deploy, Permission.Read)

  // TODO: See comment in ManagementResources.deployRequestEntity
  protected def deployProcessCommentDeprecated(
      processName: ProcessName,
      comment: Option[String] = None
  ): RouteTestResult =
    Post(
      s"/processManagement/deploy/$processName",
      HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))
    ) ~>
      withPermissions(deployRoute(), Permission.Deploy, Permission.Read)

  protected def cancelProcess(
      processName: ProcessName,
      comment: Option[String] = None
  ): RouteTestResult =
    Post(
      s"/processManagement/cancel/$processName",
      HttpEntity(
        ContentTypes.`application/json`,
        CancelRequest(comment).asJson.noSpaces
      )
    ) ~>
      withPermissions(deployRoute(), Permission.Deploy, Permission.Read)

  // TODO: See comment in ManagementResources.deployRequestEntity
  protected def cancelProcessCommentDeprecated(
      processName: ProcessName,
      comment: Option[String] = None
  ): RouteTestResult =
    Post(
      s"/processManagement/cancel/$processName",
      HttpEntity(ContentTypes.`application/json`, comment.getOrElse(""))
    ) ~>
      withPermissions(deployRoute(), Permission.Deploy, Permission.Read)

  protected def snapshot(processName: ProcessName): RouteTestResult =
    Post(s"/adminProcessManagement/snapshot/$processName") ~> withPermissions(
      deployRoute(),
      Permission.Deploy,
      Permission.Read
    )

  protected def stop(processName: ProcessName): RouteTestResult =
    Post(s"/adminProcessManagement/stop/$processName") ~> withPermissions(
      deployRoute(),
      Permission.Deploy,
      Permission.Read
    )

  protected def testScenario(scenario: CanonicalProcess, testDataContent: String): RouteTestResult = {
    val scenarioGraph = CanonicalProcessConverter.toScenarioGraph(scenario)
    val multiPart = MultipartUtils.prepareMultiParts(
      "testData"      -> testDataContent,
      "scenarioGraph" -> scenarioGraph.asJson.noSpaces
    )()
    Post(s"/processManagement/test/${scenario.name}", multiPart) ~> withPermissions(
      deployRoute(),
      Permission.Deploy,
      Permission.Read
    )
  }

  protected def getProcess(processName: ProcessName): RouteTestResult =
    Get(s"/processes/$processName") ~> withPermissions(processesRoute, Permission.Read)

  protected def getActivity(processName: ProcessName): RouteTestResult =
    Get(s"/processes/$processName/activity") ~> processActivityRouteWithAllPermissions

  protected def forScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(
      callback: ProcessJson => Unit
  ): Unit =
    tryForScenarioReturned(processName, isAdmin) { (status, response) =>
      status shouldEqual StatusCodes.OK
      val process = decodeJsonProcess(response)
      callback(process)
    }

  protected def tryForScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, String) => Unit
  ): Unit =
    Get(s"/processes/$processName") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      callback(status, responseAs[String])
    }

  private implicit val basicProcessesUnmarshaller: FromEntityUnmarshaller[List[ScenarioWithDetails]] =
    FailFastCirceSupport.unmarshaller(implicitly[Decoder[List[ScenarioWithDetails]]])

  protected def forScenariosReturned(query: ScenarioQuery, isAdmin: Boolean = false)(
      callback: List[ProcessJson] => Assertion
  ): Assertion = {
    val url = query.createQueryParamsUrl("/processes")

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = parseResponseToListJsonProcess(responseAs[String])
      responseAs[List[ScenarioWithDetails]] // just to test if decoder succeds
      callback(processes)
    }
  }

  protected def forScenariosDetailsReturned(query: ScenarioQuery, isAdmin: Boolean = false)(
      callback: List[ScenarioWithDetails] => Assertion
  ): Assertion = {
    val url = query.createQueryParamsUrl("/processesDetails")

    Get(url) ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val processes = responseAs[List[ScenarioWithDetails]]
      callback(processes)
    }
  }

  protected def routeWithPermissions(route: RouteWithUser, isAdmin: Boolean = false): Route =
    if (isAdmin) withAdminPermissions(route) else withAllPermissions(route)

  protected def toEntity[T: Encoder](data: T): HttpEntity.Strict = toEntity(implicitly[Encoder[T]].apply(data))

  private def toEntity(json: Json) = {
    val jsonString = json.printWith(humanReadablePrinter)
    HttpEntity(ContentTypes.`application/json`, jsonString)
  }

  private def prepareValidProcess(
      processName: ProcessName,
      category: TestCategory,
      isFragment: Boolean,
  ): Future[ProcessId] = {
    val validProcess: CanonicalProcess =
      if (isFragment) ProcessTestData.sampleFragmentWithInAndOut else ProcessTestData.sampleScenario
    val withNameSet = validProcess.copy(metaData = validProcess.metaData.copy(id = processName.value))
    saveAndGetId(withNameSet, category, isFragment)
  }

  private def saveAndGetId(
      process: CanonicalProcess,
      category: TestCategory,
      isFragment: Boolean,
      processingType: TestProcessingType = Streaming
  ): Future[ProcessId] = {
    val processName = process.name
    val action =
      CreateProcessAction(
        processName,
        category.stringify,
        process,
        processingType.stringify,
        isFragment,
        forwardedUserName = None
      )
    for {
      // FIXME: Using method `runInSerializableTransactionWithRetry` is a workaround for problem with flaky tests
      // (some tests failed with [java.sql.SQLTransactionRollbackException: transaction rollback: serialization failure])
      // the underlying cause of that errors needs investigating
      _  <- dbioRunner.runInSerializableTransactionWithRetry(writeProcessRepository.saveNewProcess(action))
      id <- futureFetchingScenarioRepository.fetchProcessId(processName).map(_.get)
    } yield id
  }

  protected def getProcessDetails(processId: ProcessId): ScenarioWithDetailsEntity[Unit] =
    futureFetchingScenarioRepository.fetchLatestProcessDetailsForProcessId[Unit](processId).futureValue.get

  protected def createEmptyProcess(
      processName: ProcessName,
      isFragment: Boolean = false,
  ): ProcessId = {
    val emptyProcess = newProcessPreparer.prepareEmptyProcess(processName, isFragment)
    saveAndGetId(emptyProcess, Category1, isFragment).futureValue
  }

  protected def createValidProcess(
      processName: ProcessName,
      isFragment: Boolean = false,
  ): ProcessId =
    prepareValidProcess(processName, Category1, isFragment).futureValue

  protected def createArchivedProcess(
      processName: ProcessName,
      isFragment: Boolean = false
  ): ProcessId = {
    (for {
      id <- prepareValidProcess(processName, Category1, isFragment)
      _ <- dbioRunner.runInTransaction(
        DBIOAction.seq(
          writeProcessRepository.archive(processId = ProcessIdWithName(id, processName), isArchived = true),
          actionRepository.addInstantAction(id, initialVersionId, ScenarioActionName.Archive, None)
        )
      )
    } yield id).futureValue
  }

  protected def parseResponseToListJsonProcess(response: String): List[ProcessJson] = {
    parser.decode[List[Json]](response).rightValue.map(j => ProcessJson(j))
  }

  private def decodeJsonProcess(response: String): ProcessJson =
    ProcessJson(parser.decode[Json](response).rightValue)

}

final case class ProcessVersionJson(id: Long)

object ProcessVersionJson extends OptionValues {

  def apply(process: Json): ProcessVersionJson = ProcessVersionJson(
    process.hcursor.downField("processVersionId").as[Long].toOption.value
  )

}

object ProcessJson extends OptionValues {

  def apply(process: Json): ProcessJson = {
    val lastAction = process.hcursor.downField("lastAction").as[Option[Json]].toOption.value
    val state      = process.hcursor.downField("state").as[Option[Json]].toOption.value

    new ProcessJson(
      name = process.hcursor.downField("name").as[String].toOption.value,
      lastActionVersionId = lastAction.map(_.hcursor.downField("processVersionId").as[Long].toOption.value),
      lastActionType = lastAction.map(_.hcursor.downField("actionName").as[String].toOption.value),
      state = state.map(StateJson(_)),
      processCategory = process.hcursor.downField("processCategory").as[String].toOption.value,
      isArchived = process.hcursor.downField("isArchived").as[Boolean].toOption.value,
      labels = process.hcursor.downField("labels").as[List[String]].toOption.value,
      history = process.hcursor
        .downField("history")
        .as[Option[List[Json]]]
        .toOption
        .value
        .map(_.map(v => ProcessVersionJson(v)))
    )
  }

}

final case class ProcessJson(
    name: String,
    lastActionVersionId: Option[Long],
    lastActionType: Option[String],
    state: Option[StateJson],
    processCategory: String,
    isArchived: Boolean,
    labels: List[String],
    // Process on list doesn't contain history
    history: Option[List[ProcessVersionJson]]
) {

  def isDeployed: Boolean = lastActionType.contains(ScenarioActionName.Deploy.value)

  def isCanceled: Boolean = lastActionType.contains(ScenarioActionName.Cancel.value)
}

final case class StateJson(name: String, icon: URI, tooltip: String, description: String)

object StateJson extends OptionValues {

  def apply(json: Json): StateJson = new StateJson(
    json.hcursor.downField("status").downField("name").as[String].toOption.value,
    json.hcursor.downField("icon").as[String].toOption.map(URI.create) value,
    json.hcursor.downField("tooltip").as[String].toOption.value,
    json.hcursor.downField("description").as[String].toOption.value,
  )

}

object CreateProcessResponse extends OptionValues {

  def apply(data: Json): CreateProcessResponse = CreateProcessResponse(
    data.hcursor.downField("id").as[Long].map(ProcessId(_)).toOption.value,
    data.hcursor.downField("versionId").as[Long].map(VersionId(_)).toOption.value,
    data.hcursor.downField("processName").as[String].map(ProcessName(_)).toOption.value
  )

}

final case class CreateProcessResponse(id: ProcessId, versionId: VersionId, processName: ProcessName)

object ProcessesQueryEnrichments {

  implicit class RichProcessesQuery(query: ScenarioQuery) {

    def process(): ScenarioQuery =
      query.copy(isFragment = Some(false))

    def fragment(): ScenarioQuery =
      query.copy(isFragment = Some(true))

    def unarchived(): ScenarioQuery =
      query.copy(isArchived = Some(false))

    def archived(): ScenarioQuery =
      query.copy(isArchived = Some(true))

    def deployed(): ScenarioQuery =
      query.copy(isDeployed = Some(true))

    def notDeployed(): ScenarioQuery =
      query.copy(isDeployed = Some(false))

    def withNames(names: List[String]): ScenarioQuery =
      query.copy(names = Some(names.map(ProcessName(_))))

    def withCategories(categories: List[TestCategory]): ScenarioQuery =
      withRawCategories(categories.map(_.stringify))

    def withRawCategories(categories: List[String]): ScenarioQuery =
      query.copy(categories = Some(categories))

    def withProcessingTypes(processingTypes: List[TestProcessingType]): ScenarioQuery =
      withRawProcessingTypes(processingTypes.map(_.stringify))

    def withRawProcessingTypes(processingTypes: List[String]): ScenarioQuery =
      query.copy(processingTypes = Some(processingTypes))

    def createQueryParamsUrl(path: String): String = {
      var url = s"$path?fake=true"

      query.isArchived.foreach { isArchived =>
        url += s"&isArchived=$isArchived"
      }

      query.isFragment.foreach { isFragment =>
        url += s"&isFragment=$isFragment"
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

object TestResource {

  // TODO One test from ManagementResourcesSpec and one test from ProcessesResourcesSpec use this route.
  //  The tests are still using akka based testing and it is not easy to integrate tapir route with this kind of tests.
  // should be replaced with rest call: GET /api/process/{scenarioName}/activity
  class ProcessActivityResource(
      scenarioActivityRepository: ScenarioActivityRepository,
      protected val processService: ProcessService,
      val processAuthorizer: AuthorizeProcess,
      dbioActionRunner: DBIOActionRunner,
  )(implicit val ec: ExecutionContext)
      extends Directives
      with FailFastCirceSupport
      with RouteWithUser
      with ProcessDirectives
      with AuthorizeProcessDirectives
      with NuPathMatchers {

    def securedRoute(implicit user: LoggedUser): Route = {
      path("processes" / ProcessNameSegment / "activity") { processName =>
        (get & processId(processName)) { processId =>
          complete {
            dbioActionRunner.run(scenarioActivityRepository.findActivity(processId.id))
          }
        }
      }
    }

  }

}
