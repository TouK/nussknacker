package pl.touk.nussknacker.ui.api.helpers

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.testkit.TestProbe
import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessRepository, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  val testCategoryName: String = TestPermissions.testCategoryName
  val secondTestCategoryName: String = TestPermissions.secondTestCategoryName

  //FIIXME: remove testCategory dummy implementation
  val testCategory:CategorizedPermission= Map(
    testCategoryName -> Permission.ALL_PERMISSIONS,
    secondTestCategoryName -> Permission.ALL_PERMISSIONS
  )

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleSubprocessRepository: StubSubprocessRepository = StubSubprocessRepository(Set(ProcessTestData.sampleSubprocess))

  def sampleResolver = new SubprocessResolver(prepareSampleSubprocessRepository)

  def scenarioResolver = new ScenarioResolver(sampleResolver)

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

  def newProcessActivityRepository(db: DbConfig) = new DbProcessActivityRepository(db)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRoute(adminUser())

  def createNewProcessPreparer(): NewProcessPreparer = new NewProcessPreparer(
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

  object StubSubprocessRepository {
    def apply(subprocesses: Set[CanonicalProcess]): StubSubprocessRepository =
      new StubSubprocessRepository(subprocesses.map(c => SubprocessDetails(c, testCategoryName)))
  }

  class StubSubprocessRepository(subprocesses: Set[SubprocessDetails]) extends SubprocessRepository {
    override def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails] = subprocesses
  }

}
