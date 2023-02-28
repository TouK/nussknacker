package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.server.Route
import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.CustomProcessValidatorLoader
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.subprocess.{DbSubprocessRepository, SubprocessDetails, SubprocessResolver}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.ProcessValidation
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions{

  private val dummyDbConfig: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)

  private val dummyDb: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", dummyDbConfig), HsqldbProfile)

  //FIIXME: remove testCategory dummy implementation
  val testCategory:CategorizedPermission= Map(
    TestCategories.TestCat -> Permission.ALL_PERMISSIONS,
    TestCategories.TestCat2 -> Permission.ALL_PERMISSIONS
  )

  val possibleValues: List[FixedExpressionValue] = List(FixedExpressionValue("a", "a"))

  val processValidation: ProcessValidation = ProcessTestData.processValidation.withSubprocessResolver(sampleResolver)

  val processResolving = new UIProcessResolving(processValidation, emptyProcessingTypeDataProvider)

  val buildInfo: Map[String, String] = Map("engine-version" -> "0.1")

  val posting = new ProcessPosting

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleSubprocessRepository: StubSubprocessRepository = new StubSubprocessRepository(Set(
    SubprocessDetails(ProcessTestData.sampleSubprocess, TestCategories.TestCat))
  )

  def sampleResolver = new SubprocessResolver(prepareSampleSubprocessRepository)

  def scenarioResolver = new ScenarioResolver(sampleResolver)

  def deploymentService() = new StubDeploymentService(Map.empty)

  def newDBIOActionRunner(dbs: DbConfig): DBIOActionRunner =
    DBIOActionRunner(dbs)

  def newDummyDBIOActionRunner(): DBIOActionRunner =
    newDBIOActionRunner(dummyDb)

  def newFutureFetchingProcessRepository(dbs: DbConfig) =
    new DBFetchingProcessRepository[Future](dbs) with BasicRepository

  def newFetchingProcessRepository(dbs: DbConfig) =
    new DBFetchingProcessRepository[DB](dbs) with DbioRepistory

  def newWriteProcessRepository(dbs: DbConfig, modelVersions: Option[Int] = Some(1)) =
    new DBProcessRepository(dbs, mapProcessingTypeDataProvider(modelVersions.map(TestProcessingTypes.Streaming -> _).toList: _*))

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDb)

  def newSubprocessRepository(db: DbConfig): DbSubprocessRepository =
    new DbSubprocessRepository(db, implicitly[ExecutionContext])

  def newActionProcessRepository(db: DbConfig) = new DbProcessActionRepository[DB](db,
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> buildInfo)) with DbioRepistory

  def newDummyActionRepository(): DbProcessActionRepository[DB] =
    newActionProcessRepository(dummyDb)

  def newProcessActivityRepository(db: DbConfig) = new DbProcessActivityRepository(db)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRoute(adminUser())

  def createNewProcessPreparer(): NewProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.streamingTypeSpecificInitialData),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> Map.empty)
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

  def createValidator(processDefinition: ProcessDefinition[ObjectDefinition]): ProcessValidator =
    ProcessValidator.default(ProcessDefinitionBuilder.withEmptyObjects(processDefinition), new SimpleDictRegistry(Map.empty), CustomProcessValidatorLoader.emptyCustomProcessValidator)

}
