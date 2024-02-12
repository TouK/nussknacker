package pl.touk.nussknacker.tests

import akka.http.scaladsl.server.Route
import cats.effect.unsafe.IORuntime
import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.dict.{ProcessDictSubstitutor, SimpleDictRegistry}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.tests.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.tests.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.tests.mock.{StubDeploymentService, StubFragmentRepository}
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
object TestFactory {

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

  val possibleValues: List[FixedExpressionValue] = List(FixedExpressionValue("a", "a"))

  val processValidator: UIProcessValidator = ProcessTestData.processValidator.withFragmentResolver(sampleResolver)

  val flinkProcessValidator: UIProcessValidator = ProcessTestData.processValidator
    .withFragmentResolver(sampleResolver)
    .withScenarioPropertiesConfig(FlinkStreamingPropertiesConfig.properties)

  val processValidatorByProcessingType: ProcessingTypeDataProvider[UIProcessValidator, _] =
    mapProcessingTypeDataProvider(Streaming.stringify -> flinkProcessValidator)

  val processResolver = new UIProcessResolver(
    processValidator,
    ProcessDictSubstitutor(new SimpleDictRegistry(Map.empty))
  )

  val processResolverByProcessingType: ProcessingTypeDataProvider[UIProcessResolver, _] =
    mapProcessingTypeDataProvider(Streaming.stringify -> processResolver)

  val buildInfo: Map[String, String] = Map("engine-version" -> "0.1")

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleFragmentRepository: StubFragmentRepository = new StubFragmentRepository(
    Map(
      Streaming.stringify -> List(ProcessTestData.sampleFragment)
    )
  )

  def sampleResolver = new FragmentResolver(prepareSampleFragmentRepository)

  def scenarioResolverByProcessingType: ProcessingTypeDataProvider[ScenarioResolver, _] = mapProcessingTypeDataProvider(
    Streaming.stringify -> new ScenarioResolver(sampleResolver, Streaming.stringify)
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
      mapProcessingTypeDataProvider(modelVersions.map(Streaming.stringify -> _).toList: _*)
    )

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDbRef)

  def newFragmentRepository(dbRef: DbRef): DefaultFragmentRepository =
    new DefaultFragmentRepository(newFutureFetchingScenarioRepository(dbRef))

  def newActionProcessRepository(dbRef: DbRef) =
    new DbProcessActionRepository(dbRef, mapProcessingTypeDataProvider(Streaming.stringify -> buildInfo))
      with DbioRepository

  def newDummyActionRepository(): DbProcessActionRepository =
    newActionProcessRepository(dummyDbRef)

  def newProcessActivityRepository(dbRef: DbRef) = new DbProcessActivityRepository(dbRef)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRouteWithErrorHandling(adminUser())

  val newProcessPreparer: NewProcessPreparer =
    new NewProcessPreparer(
      ProcessTestData.streamingTypeSpecificInitialData,
      FlinkStreamingPropertiesConfig.properties
    )

  val newProcessPreparerByProcessingType: ProcessingTypeDataProvider[NewProcessPreparer, _] =
    mapProcessingTypeDataProvider(
      Streaming.stringify -> newProcessPreparer
    )

  def withPermissions(route: RouteWithUser, permissions: Permission.Permission*): Route =
    route.securedRouteWithErrorHandling(user(permissions = permissions))

  // FIXME: update
  def withAllPermissions(route: RouteWithUser): Route = withPermissions(route, Permission.ALL_PERMISSIONS.toSeq: _*)

  def withAdminPermissions(route: RouteWithUser): Route = route.securedRouteWithErrorHandling(adminUser())

  def withoutPermissions(route: RouteWithoutUser): Route = route.publicRouteWithErrorHandling()

  // FIXME: update
  def user(
      id: String = "1",
      username: String = "user",
      permissions: Iterable[Permission.Permission] = List.empty
  ): LoggedUser =
    LoggedUser(
      id,
      username,
      Map(TestCategory.Default.stringify -> permissions.toSet),
      globalPermissions = List("CustomFixedPermission")
    )

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser =
    LoggedUser(id, username, Map.empty, isAdmin = true)

  def mapProcessingTypeDataProvider[T](data: (String, T)*): ProcessingTypeDataProvider[T, Nothing] = {
    // TODO: tests for user privileges
    ProcessingTypeDataProvider.withEmptyCombinedData(
      data.toMap.map { case (k, v) => (k, ValueWithPermission.anyUser(v)) }
    )
  }

  def createCategoryService(designerConfig: Config): ProcessCategoryService =
    ConfigProcessCategoryService(
      ProcessingTypeDataConfigurationReader
        .readProcessingTypeConfig(ConfigWithUnresolvedVersion(designerConfig))
        .mapValuesNow(_.category)
    )

}
