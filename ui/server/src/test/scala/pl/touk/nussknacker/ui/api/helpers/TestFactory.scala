package pl.touk.nussknacker.ui.api.helpers

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.testkit.TestProbe
import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  val testCategoryName: String = TestPermissions.testCategoryName
  val secondTestCategoryName: String = TestPermissions.secondTestCategoryName

  //FIIXME: remove testCategory dommy implementation
  val testCategory:CategorizedPermission= Map(
    testCategoryName -> Permission.ALL_PERMISSIONS,
    secondTestCategoryName -> Permission.ALL_PERMISSIONS
  )

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleSubprocessRepository: StubSubprocessRepository = StubSubprocessRepository(Set(ProcessTestData.sampleSubprocess))

  val sampleResolver = new SubprocessResolver(prepareSampleSubprocessRepository)

  val possibleValues = List(FixedExpressionValue("a", "a"))
  val processValidation = new ProcessValidation(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.validator),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map()),
    sampleResolver,
    emptyProcessingTypeDataProvider
  )
  val processResolving = new UIProcessResolving(processValidation, emptyProcessingTypeDataProvider)
  val posting = new ProcessPosting
  val buildInfo: Map[String, String] = Map("engine-version" -> "0.1")

  val processWithInvalidAdditionalProperties: DisplayableProcess = DisplayableProcess(
    id = "fooProcess",
    properties = ProcessProperties(StreamMetaData(
      Some(2)),
      ExceptionHandlerRef(List.empty),
      Some(ProcessAdditionalFields(Some("scenario description"), Map(
        "maxEvents" -> "text",
        "unknown" -> "x",
        "numberOfThreads" -> "wrong fixed value"
      ))),
      subprocessVersions = Map.empty),
    nodes = List.empty,
    edges = List.empty,
    processingType = TestProcessingTypes.Streaming
  )

  private val dummyDbConfig: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)
  private val dummyDb: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", dummyDbConfig), HsqldbProfile)


  def newDummyManagerActor(): ActorRef = TestProbe()(ActorSystem("DefaultComponentServiceSpec")).ref

  def newDBRepositoryManager(dbs: DbConfig): RepositoryManager[DB] =
    RepositoryManager.createDbRepositoryManager(dbs)

  def newDummyRepositoryManager(): RepositoryManager[DB] =
    newDBRepositoryManager(dummyDb)

  def newFetchingProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBFetchingProcessRepository[Future](dbs) with BasicRepository

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBProcessRepository(dbs, mapProcessingTypeDataProvider(modelVersions.map(TestProcessingTypes.Streaming -> _).toList: _*))

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDb)

  def newSubprocessRepository(db: DbConfig): DbSubprocessRepository =
    new DbSubprocessRepository(db, implicitly[ExecutionContext])

  def newActionProcessRepository(db: DbConfig) = new DbProcessActionRepository(db,
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> buildInfo))

  def newDummyActionRepository(): DbProcessActionRepository =
    newActionProcessRepository(dummyDb)

  def newProcessActivityRepository(db: DbConfig) = new ProcessActivityRepository(db)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRoute(adminUser())

  def createNewProcessPreparer(): NewProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider("streaming" ->  ProcessTestData.processDefinition),
    mapProcessingTypeDataProvider("streaming" -> ProcessTestData.streamingTypeSpecificInitialData),
    mapProcessingTypeDataProvider("streaming" -> Map.empty)
  )

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission): Route =
    route.securedRoute(user(permissions = permissions))

  //FIXME: update
  def withAllPermissions(route: RouteWithUser): Route = withPermissions(route, testPermissionAll)

  def withAdminPermissions(route: RouteWithUser): Route = route.securedRoute(adminUser())

  def withoutPermissions(route: RouteWithoutUser): Route = route.publicRoute()

  def userWithCategoriesReadPermission(id: String = "1", username: String = "user", categories: List[String]): LoggedUser =
    user(id, username, categories.map(c => c -> Set(Permission.Read)).toMap)

  //FIXME: update
  def user(id: String = "1", username: String = "user", permissions: CategorizedPermission = testPermissionEmpty): LoggedUser =
    LoggedUser(id, username, permissions, globalPermissions = List("CustomFixedPermission"))

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser = LoggedUser(id, username, Map.empty, Nil, isAdmin = true)

  def mapProcessingTypeDataProvider[T](data: (ProcessingType, T)*) = new MapBasedProcessingTypeDataProvider[T](Map(data: _*))

  def emptyProcessingTypeDataProvider = new MapBasedProcessingTypeDataProvider[Nothing](Map.empty)

  object MockDeploymentManager {
    val savepointPath = "savepoints/123-savepoint"
    val stopSavepointPath = "savepoints/246-stop-savepoint"
  }

  class MockDeploymentManager(val defaultProcessStateStatus: StateStatus) extends FlinkDeploymentManager(ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig).toModelData, shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED"){

    import MockDeploymentManager._

    def this() {
      this(SimpleStateStatus.Running)
    }

    private def prepareProcessState(status: StateStatus): Option[ProcessState ]=
      prepareProcessState(status, Some(ProcessVersion.empty))

    private def prepareProcessState(status: StateStatus, version: Option[ProcessVersion]): Option[ProcessState] =
      Some(SimpleProcessState(ExternalDeploymentId("1"), status, version))

    override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] =
      Future.successful(managerProcessState.get())

    override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData,
                        processDeploymentData: ProcessDeploymentData, savepoint: Option[String]): Future[Option[ExternalDeploymentId]] = {
      deploys.add(processVersion)
      deployResult
    }

    private var deployResult: Future[Option[ExternalDeploymentId]] = Future.successful(None)

    private val managerProcessState = new AtomicReference[Option[ProcessState]](prepareProcessState(defaultProcessStateStatus))

    //queue of invocations to e.g. check that deploy was already invoked in "ProcessManager"
    val deploys = new ConcurrentLinkedQueue[ProcessVersion]()

    def withWaitForDeployFinish[T](action: => T): T = {
      val promise = Promise[Option[ExternalDeploymentId]]
      try {
        deployResult = promise.future
        action
      } finally {
        promise.complete(Try(None))
        deployResult = Future.successful(None)
      }
    }

    def withFailingDeployment[T](action: => T): T = {
      deployResult = Future.failed(new RuntimeException("Failing deployment..."))
      try {
        action
      } finally {
        deployResult = Future.successful(None)
      }
    }

    def withProcessFinished[T](action: => T): T = {
      withProcessStateStatus(SimpleStateStatus.Finished)(action)
    }

    def withProcessStateStatus[T](status: StateStatus)(action: => T): T = {
      withProcessState(prepareProcessState(status))(action)
    }

    def withProcessStateVersion[T](status: StateStatus, version: Option[ProcessVersion])(action: => T): T = {
      withProcessState(prepareProcessState(status, version))(action)
    }

    def withEmptyProcessState[T](action: => T): T = {
      withProcessState(None)(action)
    }

    def withProcessState[T](status: Option[ProcessState])(action: => T): T = {
      try {
        managerProcessState.set(status)
        action
      } finally {
        managerProcessState.set(prepareProcessState(defaultProcessStateStatus))
      }
    }

    override protected def cancel(deploymentId: ExternalDeploymentId): Future[Unit] = Future.successful(Unit)

    override protected def makeSavepoint(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

    override protected def stop(deploymentId: ExternalDeploymentId, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

    override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = ???

    override def customActions: List[CustomAction] = {
      import SimpleStateStatus._
      List(
        CustomAction(name = "hello",            allowedStateStatusNames = List(Warning.name, NotDeployed.name)),
        CustomAction(name = "not-implemented",  allowedStateStatusNames = List(Warning.name, NotDeployed.name)),
        CustomAction(name = "invalid-status",   allowedStateStatusNames = Nil)
      )
    }

    override def invokeCustomAction(actionRequest: CustomActionRequest,
                                    processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
      Future.successful {
        actionRequest.name match {
          case "hello" | "invalid-status" => Right(CustomActionResult(actionRequest, "Hi"))
          case _ => Left(CustomActionNotImplemented(actionRequest))
        }
    }

    override def close(): Unit = {}

    override def cancel(name: ProcessName, user: User): Future[Unit] = Future.successful(Unit)

    override protected def checkRequiredSlotsExceedAvailableSlots(processDeploymentData: ProcessDeploymentData, currentlyDeployedJobId: Option[ExternalDeploymentId]): Future[Unit] = Future.successful(())

  }

  object StubSubprocessRepository {
    def apply(subprocesses: Set[CanonicalProcess]): StubSubprocessRepository =
      new StubSubprocessRepository(subprocesses.map(c => SubprocessDetails(c, testCategoryName)))
  }

  class StubSubprocessRepository(subprocesses: Set[SubprocessDetails]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = subprocesses
  }

}
