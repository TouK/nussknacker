package pl.touk.nussknacker.test.utils.domain

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.IORuntime
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.{DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.deployment.{
  ProcessingTypeActionServiceStub,
  ProcessingTypeDeployedScenariosProviderStub
}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.dict.{ProcessDictSubstitutor, SimpleDictRegistry}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelDependencies}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.{TestCategory, TestProcessingType}
import pl.touk.nussknacker.test.mock.{StubFragmentRepository, StubProcessStateProvider, TestAdditionalUIConfigProvider}
import pl.touk.nussknacker.ui.api.{RouteWithUser, RouteWithoutUser}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.processingtype.{
  ScenarioParametersService,
  ScenarioParametersWithEngineSetupErrors,
  ValueWithRestriction
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import pl.touk.nussknacker.ui.process.version.{ScenarioGraphVersionRepository, ScenarioGraphVersionService}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import sttp.client3.testing.SttpBackendStub

import java.time.Clock
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

  val scenarioParametersService: ScenarioParametersService = {
    val combinations = Map(
      TestProcessingType.Streaming.stringify ->
        ScenarioParametersWithEngineSetupErrors(
          ScenarioParameters(
            ProcessingMode.UnboundedStream,
            TestCategory.Category1.stringify,
            EngineSetupName("Flink")
          ),
          List.empty
        )
    )
    ScenarioParametersService.createUnsafe(combinations)
  }

  val scenarioParametersServiceProvider: ProcessingTypeDataProvider[_, ScenarioParametersService] =
    ProcessingTypeDataProvider(Map.empty, scenarioParametersService)

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

  val modelDependencies: ModelDependencies =
    ModelDependencies(
      TestAdditionalUIConfigProvider.componentAdditionalConfigMap,
      componentId => DesignerWideComponentId(componentId.toString),
      workingDirectoryOpt = None,
      _ => true
    )

  val deploymentManagerDependencies: DeploymentManagerDependencies = {
    val actorSystem = ActorSystem("TestFactory")
    DeploymentManagerDependencies(
      new ProcessingTypeDeployedScenariosProviderStub(List.empty),
      new ProcessingTypeActionServiceStub,
      actorSystem.dispatcher,
      actorSystem,
      SttpBackendStub.asynchronousFuture
    )
  }

  def processStateProvider() = new StubProcessStateProvider(Map.empty)

  def newDBIOActionRunner(dbRef: DbRef): DBIOActionRunner =
    DBIOActionRunner(dbRef)

  def newDummyDBIOActionRunner(): DBIOActionRunner =
    newDBIOActionRunner(dummyDbRef)

  def newScenarioActivityRepository(dbRef: DbRef, clock: Clock) = new DbScenarioActivityRepository(dbRef, clock)

  def newFutureFetchingScenarioRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[Future](dbRef, newActionProcessRepository(dbRef)) with BasicRepository

  def newFetchingProcessRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[DB](dbRef, newActionProcessRepository(dbRef)) with DbioRepository

  def newWriteProcessRepository(dbRef: DbRef, clock: Clock, modelVersions: Option[Int] = Some(1)) =
    new DBProcessRepository(
      dbRef,
      clock,
      newScenarioActivityRepository(dbRef, clock),
      mapProcessingTypeDataProvider(modelVersions.map(Streaming.stringify -> _).toList: _*),
    )

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDbRef, Clock.systemUTC())

  def newScenarioGraphVersionService(dbRef: DbRef) = new ScenarioGraphVersionService(
    newScenarioGraphVersionRepository(dbRef),
    mapProcessingTypeDataProvider(Streaming.stringify -> processValidator),
    scenarioResolverByProcessingType,
    newDBIOActionRunner(dbRef)
  )

  def newScenarioGraphVersionRepository(dbRef: DbRef) = new ScenarioGraphVersionRepository(dbRef)

  def newFragmentRepository(dbRef: DbRef): DefaultFragmentRepository =
    new DefaultFragmentRepository(newFutureFetchingScenarioRepository(dbRef))

  def newActionProcessRepository(dbRef: DbRef) =
    new DbScenarioActionRepository(
      dbRef,
      mapProcessingTypeDataProvider(Streaming.stringify -> buildInfo)
    ) with DbioRepository

  def newDummyActionRepository(): DbScenarioActionRepository =
    newActionProcessRepository(dummyDbRef)

  def newScenarioMetadataRepository(dbRef: DbRef) = new ScenarioMetadataRepository(dbRef)

  def newDeploymentRepository(dbRef: DbRef, clock: Clock) = new DeploymentRepository(dbRef, clock)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRouteWithErrorHandling(adminUser())

  val newProcessPreparer: NewProcessPreparer =
    new NewProcessPreparer(
      ProcessTestData.streamingTypeSpecificInitialData,
      FlinkStreamingPropertiesConfig.properties,
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify)
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
    RealLoggedUser(
      id,
      username,
      Map(TestCategory.Category1.stringify -> permissions.toSet),
      globalPermissions = List("CustomFixedPermission")
    )

  def adminUser(id: String = "1", username: String = "admin"): LoggedUser =
    RealLoggedUser(id, username, Map.empty, isAdmin = true)

  def mapProcessingTypeDataProvider[T](data: (String, T)*): ProcessingTypeDataProvider[T, Nothing] = {
    // TODO: tests for user privileges
    ProcessingTypeDataProvider.withEmptyCombinedData(
      Map(data: _*).mapValuesNow(ValueWithRestriction.anyUser)
    )
  }

}
