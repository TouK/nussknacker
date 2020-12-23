package pl.touk.nussknacker.ui.api.helpers

import java.util.concurrent.atomic.AtomicReference
import akka.http.scaladsl.server.Route
import cats.instances.future._
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessState, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{CustomActionErr, CustomActionReq, CustomActionRes, DeploymentId, ProcessDeploymentData, ProcessState, SavepointResult, StateStatus, User}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.management.FlinkProcessManager
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{DBFetchingProcessRepository, _}
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util.ConfigWithScalaVersion
import pl.touk.nussknacker.ui.validation.ProcessValidation

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

  val sampleSubprocessRepository = new SampleSubprocessRepository(Set(ProcessTestData.sampleSubprocess))
  val sampleResolver = new SubprocessResolver(sampleSubprocessRepository)

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
      isSubprocess = false,
      Some(ProcessAdditionalFields(Some("process description"), Set.empty, Map(
        "maxEvents" -> "text",
        "unknown" -> "x",
        "numberOfThreads" -> "wrong fixed value"
      ))),
      subprocessVersions = Map.empty),
    nodes = List.empty,
    edges = List.empty,
    processingType = TestProcessingTypes.Streaming
  )

  def newProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBFetchingProcessRepository[Future](dbs) with BasicRepository

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DbWriteProcessRepository[Future](dbs, mapProcessingTypeDataProvider(modelVersions.map(TestProcessingTypes.Streaming -> _).toList: _*))
        with WriteProcessRepository with BasicRepository

  def newSubprocessRepository(db: DbConfig): DbSubprocessRepository = {
    new DbSubprocessRepository(db, implicitly[ExecutionContext])
  }

  def newDeploymentProcessRepository(db: DbConfig) = new ProcessActionRepository(db,
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> buildInfo))

  def newProcessActivityRepository(db: DbConfig) = new ProcessActivityRepository(db)

  def asAdmin(route: RouteWithUser): Route = {
    route.securedRoute(adminUser())
  }

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission): Route =
    route.securedRoute(user(permissions = permissions))

  //FIXME: update
  def withAllPermissions(route: RouteWithUser): Route = withPermissions(route, testPermissionAll)

  def withAdminPermissions(route: RouteWithUser): Route = route.securedRoute(adminUser())

  def withoutPermissions(route: RouteWithoutUser): Route = route.publicRoute()


  //FIXME: update
  def user(id: String = "1", username: String = "user", permissions: CategorizedPermission = testPermissionEmpty): LoggedUser = LoggedUser(id, username, permissions)

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser = LoggedUser(id, username, Map.empty, Nil, isAdmin = true)

  def mapProcessingTypeDataProvider[T](data: (ProcessingType, T)*) = new MapBasedProcessingTypeDataProvider[T](Map(data: _*))

  def emptyProcessingTypeDataProvider = new MapBasedProcessingTypeDataProvider[Nothing](Map.empty)

  object MockProcessManager {
    val savepointPath = "savepoints/123-savepoint"
    val stopSavepointPath = "savepoints/246-stop-savepoint"
  }

  class MockProcessManager extends FlinkProcessManager(ProcessingTypeConfig.read(ConfigWithScalaVersion.streamingProcessTypeConfig).toModelData, shouldVerifyBeforeDeploy = false, mainClassName = "UNUSED"){

    import MockProcessManager._

    private def prepareProcessState(status: StateStatus): Option[ProcessState ]=
      prepareProcessState(status, Some(ProcessVersion.empty))

    private def prepareProcessState(status: StateStatus, version: Option[ProcessVersion]): Option[ProcessState] =
      Some(SimpleProcessState(DeploymentId("1"), status, version))

    override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] =
      Future.successful(managerProcessState.get())

    override def deploy(processId: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepoint: Option[String], user: User): Future[Unit] =
      deployResult

    private var deployResult: Future[Unit] = Future.successful(())

    private val managerProcessState = new AtomicReference[Option[ProcessState]](prepareProcessState(SimpleStateStatus.Running))

    def withWaitForDeployFinish[T](action: => T): T = {
      val promise = Promise[Unit]
      try {
        deployResult = promise.future
        action
      } finally {
        promise.complete(Try(Unit))
        deployResult = Future.successful(())
      }
    }

    def withFailingDeployment[T](action: => T): T = {
      deployResult = Future.failed(new RuntimeException("Failing deployment..."))
      try {
        action
      } finally {
        deployResult = Future.successful(())
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
        managerProcessState.set(prepareProcessState(SimpleStateStatus.Running))
      }
    }

    override protected def cancel(job: ProcessState): Future[Unit] = Future.successful(Unit)

    override protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = savepointPath))

    override protected def stop(job: ProcessState, savepointDir: Option[String]): Future[SavepointResult] = Future.successful(SavepointResult(path = stopSavepointPath))

    override protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit] = ???

    override def customAction(customAction: CustomActionReq): Future[Either[CustomActionErr, CustomActionRes]] = Future.successful {
      customAction.name match {
        case "hello" => Right(CustomActionRes("Hi"))
        case _ => Left(CustomActionErr("Invalid action"))
      }
    }

    override def close(): Unit = {}
  }

  class SampleSubprocessRepository(subprocesses: Set[CanonicalProcess]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] =
      subprocesses.map(c => SubprocessDetails(c, testCategoryName))
  }
}
