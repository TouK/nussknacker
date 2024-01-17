package pl.touk.nussknacker.ui.api.helpers

import akka.http.scaladsl.server.Route
import cats.effect.unsafe.IORuntime
import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.{ProcessDictSubstitutor, SimpleDictRegistry}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{ConfigWithUnresolvedVersion, CustomProcessValidatorLoader}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.helpers.TestPermissions.CategorizedPermission
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.processingtypedata.{
  ProcessingTypeDataConfigurationReader,
  ProcessingTypeDataProvider,
  ValueWithPermission
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, NewProcessPreparer, ProcessCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

//TODO: merge with ProcessTestData?
object TestFactory extends TestPermissions {

  val (dummyDbRef: DbRef, _) = {
    val dbConfig = ConfigFactory.parseMap(
      Map(
        "db" -> Map(
          "user"     -> "SA",
          "password" -> "",
          "url"      -> "jdbc:hsqldb:mem:esp;sql.syntax_ora=true",
          "driver"   -> "org.hsqldb.jdbc.JDBCDriver"
        ).asJava
      ).asJava
    )
    DbRef.create(dbConfig).allocated.unsafeRunSync()(IORuntime.global)
  }

  // FIXME: remove testCategory dummy implementation
  val testCategory: CategorizedPermission = Map(
    TestCategories.Category1 -> Permission.ALL_PERMISSIONS,
    TestCategories.Category2 -> Permission.ALL_PERMISSIONS
  )

  val possibleValues: List[FixedExpressionValue] = List(FixedExpressionValue("a", "a"))

  val processValidator: UIProcessValidator = ProcessTestData.processValidator.withFragmentResolver(sampleResolver)

  val flinkProcessValidator: UIProcessValidator = ProcessTestData.processValidator
    .withFragmentResolver(sampleResolver)
    .withScenarioPropertiesConfig(FlinkStreamingPropertiesConfig.properties)

  val processValidatorByProcessingType: ProcessingTypeDataProvider[UIProcessValidator, _] =
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> flinkProcessValidator)

  val processResolver = new UIProcessResolver(
    processValidator,
    ProcessDictSubstitutor(new SimpleDictRegistry(Map.empty))
  )

  val processResolverByProcessingType: ProcessingTypeDataProvider[UIProcessResolver, _] =
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> processResolver)

  val buildInfo: Map[String, String] = Map("engine-version" -> "0.1")

  val posting = new ProcessPosting

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleFragmentRepository: StubFragmentRepository = new StubFragmentRepository(
    Map(
      TestProcessingTypes.Streaming -> List(ProcessTestData.sampleFragment)
    )
  )

  def sampleResolver = new FragmentResolver(prepareSampleFragmentRepository)

  def scenarioResolverByProcessingType: ProcessingTypeDataProvider[ScenarioResolver, _] = mapProcessingTypeDataProvider(
    TestProcessingTypes.Streaming -> new ScenarioResolver(sampleResolver, TestProcessingTypes.Streaming)
  )

  def deploymentService() = new StubDeploymentService(Map.empty)

  def newDBIOActionRunner(dbRef: DbRef): DBIOActionRunner =
    DBIOActionRunner(dbRef)

  def newDummyDBIOActionRunner(): DBIOActionRunner =
    newDBIOActionRunner(dummyDbRef)

  def newFutureFetchingScenarioRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[Future](dbRef, newActionProcessRepository(dbRef)) with BasicRepository

  def newFetchingProcessRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[DB](dbRef, newActionProcessRepository(dbRef)) with DbioRepository

  def newWriteProcessRepository(dbRef: DbRef, modelVersions: Option[Int] = Some(1)) =
    new DBProcessRepository(
      dbRef,
      mapProcessingTypeDataProvider(modelVersions.map(TestProcessingTypes.Streaming -> _).toList: _*)
    )

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDbRef)

  def newFragmentRepository(dbRef: DbRef): DefaultFragmentRepository =
    new DefaultFragmentRepository(newFutureFetchingScenarioRepository(dbRef))

  def newActionProcessRepository(dbRef: DbRef) =
    new DbProcessActionRepository[DB](dbRef, mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> buildInfo))
      with DbioRepository

  def newDummyActionRepository(): DbProcessActionRepository[DB] =
    newActionProcessRepository(dummyDbRef)

  def newProcessActivityRepository(dbRef: DbRef) = new DbProcessActivityRepository(dbRef)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRouteWithErrorHandling(adminUser())

  def createNewProcessPreparer(): NewProcessPreparer = new NewProcessPreparer(
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> ProcessTestData.streamingTypeSpecificInitialData),
    mapProcessingTypeDataProvider(TestProcessingTypes.Streaming -> FlinkStreamingPropertiesConfig.properties)
  )

  def withPermissions(route: RouteWithUser, permissions: TestPermissions.CategorizedPermission): Route =
    route.securedRouteWithErrorHandling(user(permissions = permissions))

  // FIXME: update
  def withAllPermissions(route: RouteWithUser): Route = withPermissions(route, testPermissionAll)

  def withAdminPermissions(route: RouteWithUser): Route = route.securedRouteWithErrorHandling(adminUser())

  def withoutPermissions(route: RouteWithoutUser): Route = route.publicRouteWithErrorHandling()

  def userWithCategoriesReadPermission(
      id: String = "1",
      username: String = "user",
      categories: List[String]
  ): LoggedUser =
    user(id, username, categories.map(c => c -> Set(Permission.Read)).toMap)

  // FIXME: update
  def user(
      id: String = "1",
      username: String = "user",
      permissions: CategorizedPermission = testPermissionEmpty
  ): LoggedUser =
    LoggedUser(id, username, permissions, globalPermissions = List("CustomFixedPermission"))

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser =
    LoggedUser(id, username, Map.empty, Nil, isAdmin = true)

  def mapProcessingTypeDataProvider[T](data: (ProcessingType, T)*): ProcessingTypeDataProvider[T, Nothing] = {
    // TODO: tests for user privileges
    ProcessingTypeDataProvider.withEmptyCombinedData(
      Map(data: _*).mapValuesNow(ValueWithPermission.anyUser)
    )
  }

  def emptyProcessingTypeDataProvider: ProcessingTypeDataProvider[Nothing, Nothing] =
    ProcessingTypeDataProvider.withEmptyCombinedData(Map.empty)

  def createValidator(modelDefinition: ModelDefinition[ComponentDefinitionWithImplementation]): ProcessValidator = {
    ProcessValidator.default(
      ModelDefinitionWithClasses(modelDefinition),
      new SimpleDictRegistry(Map.empty),
      CustomProcessValidatorLoader.emptyCustomProcessValidator
    )
  }

  def createCategoryService(designerConfig: Config): ProcessCategoryService =
    ConfigProcessCategoryService(
      ProcessingTypeDataConfigurationReader
        .readProcessingTypeConfig(ConfigWithUnresolvedVersion(designerConfig))
        .mapValuesNow(_.category)
    )

}
