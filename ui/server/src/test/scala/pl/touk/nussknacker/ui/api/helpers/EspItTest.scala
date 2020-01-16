package pl.touk.nussknacker.ui.api.helpers

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.instances.all._
import cats.syntax.semigroup._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Json, Printer}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingProcessManagerProvider
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.db.entity.DeployedProcessInfoEntityData
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
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
  val deploymentProcessRepository = newDeploymentProcessRepository(db)
  val processActivityRepository = newProcessActivityRepository(db)

  val typesForCategories = new ProcessTypesForCategories(testConfig)

  val existingProcessingType = "streaming"

  val processManager = new MockProcessManager
  val processChangeListener = new TestProcessChangeListener()
  def createManagementActorRef = {
    system.actorOf(ManagementActor.props(env,
      Map(TestProcessingTypes.Streaming -> processManager),
      processRepository,
      deploymentProcessRepository,
      TestFactory.sampleResolver,
      processChangeListener), "management")
  }
  val managementActor: ActorRef = createManagementActorRef
  val jobStatusService = new JobStatusService(managementActor)
  val newProcessPreparer = new NewProcessPreparer(
    Map("streaming" ->  ProcessTestData.processDefinition),
    Map("streaming" -> (_ => StreamMetaData(None))),
    Map("streaming" -> Map.empty)
  )

  private implicit val user: LoggedUser = TestFactory.adminUser("user")

  val processesRoute = new ProcessesResources(
    processRepository = processRepository,
    writeRepository = writeProcessRepository,
    jobStatusService = jobStatusService,
    processActivityRepository = processActivityRepository,
    processValidation = processValidation,
    processResolving = processResolving,
    typesForCategories = typesForCategories,
    newProcessPreparer = newProcessPreparer,
    processAuthorizer = processAuthorizer,
    processChangeListener = processChangeListener
  )

  val authenticationConfig = DefaultAuthenticationConfiguration.create(testConfig)
  val analyticsConfig = AnalyticsConfig(testConfig)

  val featureTogglesConfig = FeatureTogglesConfig.create(testConfig)
  val typeToConfig = ProcessingTypeDeps(testConfig, featureTogglesConfig.standaloneMode)
  val usersRoute = new UserResources(typesForCategories)
  val settingsRoute = new SettingsResources(featureTogglesConfig, typeToConfig, authenticationConfig, analyticsConfig)

  val processesExportResources = new ProcessesExportResources(processRepository, processActivityRepository, processResolving)
  val definitionResources = new DefinitionResources(
    Map(existingProcessingType ->  FlinkStreamingProcessManagerProvider.defaultModelData(testConfig)), subprocessRepository, typesForCategories)

  val processesRouteWithAllPermissions = withAllPermissions(processesRoute)

  val settingsRouteWithoutPermissions = withoutPermissions(settingsRoute)

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

  def getSampleProcess: RouteTestResult = {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, testPermissionRead)
  }

  def getProcess(processId: String): RouteTestResult = {
    Get(s"/processes/$processId") ~> withPermissions(processesRoute, testPermissionRead)
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

  def prepareDeploy(id: process.ProcessId): Future[DeployedProcessInfoEntityData] =
    deploymentProcessRepository.markProcessAsDeployed(id, 1, "stream", env, Some("Deploy comment"))

  def prepareCancel(id: process.ProcessId): Future[DeployedProcessInfoEntityData] =
    deploymentProcessRepository.markProcessAsCancelled(id, 1, env, Some("Cancel comment"))

  def createProcess(processName: ProcessName, category: String, isSubprocess: Boolean): process.ProcessId = {
    prepareProcess(processName, category, isSubprocess).futureValue
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
  def createDeployedProcess(processName: String): process.ProcessId =
    createDeployedProcess(ProcessName(processName), testCategoryName, false)

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
    prepareCancel(id).map(_ => ()).futureValue shouldBe ()
}
