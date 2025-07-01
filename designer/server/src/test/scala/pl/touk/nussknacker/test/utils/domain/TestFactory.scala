package pl.touk.nussknacker.test.utils.domain

import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import db.util.DBIOActionInstances._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.engine.{DeploymentManagerDependencies, ModelData, ModelDependencies}
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.definition.{EngineScenarioCompilationDependencies, FixedExpressionValue}
import pl.touk.nussknacker.engine.definition.action.ModelDataActionInfoProvider
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.dict.{ProcessDictSubstitutor, SimpleDictRegistry}
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestProcessingType.Streaming1
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestCategory
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{StubFragmentRepository, TestAdditionalUIConfigProvider}
import pl.touk.nussknacker.ui.api.{RouteWithoutUser, RouteWithUser, ScenarioStatusPresenter}
import pl.touk.nussknacker.ui.db.DbRefInstance
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.{DBProcessService, NewProcessPreparer, ProcessService}
import pl.touk.nussknacker.ui.process.deployment.{ActionInfoService, DeploymentManagerDispatcher, ScenarioResolver}
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.{DefaultFragmentRepository, FragmentResolver}
import pl.touk.nussknacker.ui.process.newdeployment.DeploymentRepository
import pl.touk.nussknacker.ui.process.processingtype.{
  ScenarioParametersService,
  ScenarioParametersWithEngineSetupErrors,
  ValueWithRestriction
}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
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
          "driver"   -> "org.hsqldb.jdbc.JDBCDriver",
          "schema"   -> "testschema"
        ).asJava
      ).asJava
    )
    DbRefInstance.create(dbConfig).allocated.unsafeRunSync()(IORuntime.global)
  }

  val possibleValues: List[FixedExpressionValue] = List(FixedExpressionValue("a", "a"))

  def processValidator(processingTypes: List[String] = List(Streaming.stringify)): UIProcessValidator =
    ProcessTestData.testProcessValidator(fragmentResolver = sampleResolver(processingTypes))

  def flinkProcessValidator(processingTypes: List[String] = List(Streaming.stringify)): UIProcessValidator =
    ProcessTestData.testProcessValidator(
      fragmentResolver = sampleResolver(processingTypes),
      scenarioProperties = FlinkStreamingPropertiesConfig.properties
    )

  def processValidatorByProcessingType(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ProcessingTypeDataProvider[UIProcessValidator, _] = {
    val validator = flinkProcessValidator(processingTypes)
    mapProcessingTypeDataProvider(processingTypes.map(pt => (pt, validator)): _*)
  }

  def processResolver(processingTypes: List[String] = List(Streaming.stringify)) = new UIProcessResolver(
    processValidator(processingTypes),
    ProcessDictSubstitutor(new SimpleDictRegistry(Map.empty))
  )

  def processResolverByProcessingType(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ProcessingTypeDataProvider[UIProcessResolver, _] = {
    val resolver = processResolver(processingTypes)
    mapProcessingTypeDataProvider(processingTypes.map(pt => (pt, resolver)): _*)
  }

  def scenarioParametersService(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ScenarioParametersService = {
    val scenarioParameters = ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(
        ProcessingMode.UnboundedStream,
        TestCategory.Category1.stringify,
        EngineSetupName("Flink")
      ),
      List.empty
    )
    val combinations = processingTypes.map(pt => (pt, scenarioParameters)).toMap
    ScenarioParametersService.createUnsafe(combinations)
  }

  def scenarioParametersServiceProvider(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ProcessingTypeDataProvider[_, ScenarioParametersService] =
    TestProcessingTypeDataProviderFactory.create(Map.empty, scenarioParametersService(processingTypes))

  // It should be defined as method, because when it's defined as val then there is bug in IDEA at DefinitionPreparerSpec - it returns null
  def prepareSampleFragmentRepository(
      processingTypes: List[String] = List(Streaming.stringify)
  ): StubFragmentRepository =
    new StubFragmentRepository(
      processingTypes.map(pt => (pt, List(ProcessTestData.sampleFragment))).toMap
    )

  def sampleResolver(processingTypes: List[String] = List(Streaming.stringify)) =
    new FragmentResolver(prepareSampleFragmentRepository(processingTypes))

  def scenarioResolver(processingType: String = Streaming.stringify) =
    new ScenarioResolver(sampleResolver(processingType :: Nil), processingType)

  def scenarioResolverByProcessingType(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ProcessingTypeDataProvider[ScenarioResolver, _] = {
    mapProcessingTypeDataProvider(processingTypes.map(pt => (pt, scenarioResolver(pt))): _*)
  }

  def additionalComponentConfigsByProcessingType
      : ProcessingTypeDataProvider[Map[DesignerWideComponentId, ComponentAdditionalConfig], _] =
    mapProcessingTypeDataProvider()

  val modelDependencies: ModelDependencies =
    ModelDependencies(
      TestAdditionalUIConfigProvider.componentAdditionalConfigMap,
      componentId => DesignerWideComponentId(componentId.toString),
      workingDirectoryOpt = None,
      ComponentDefinitionExtractionMode.FinalAndBasicDefinitions,
      designerDbRef = None
    )

  val deploymentManagerDependencies: DeploymentManagerDependencies = {
    val actorSystem = ActorSystem("TestFactory")
    new DeploymentManagerDependencies(
      actorSystem.dispatcher,
      IORuntime.global,
      actorSystem,
      SttpBackendStub.asynchronousFuture
    )
  }

  def newDBIOActionRunner(dbRef: DbRef): DBIOActionRunner =
    DBIOActionRunner(dbRef)

  def newDummyDBIOActionRunner(): DBIOActionRunner =
    newDBIOActionRunner(dummyDbRef)

  def newScenarioActivityRepository(dbRef: DbRef, clock: Clock) = DbScenarioActivityRepository.create(dbRef, clock)

  def newScenarioLabelsRepository(dbRef: DbRef) = new ScenarioLabelsRepository(dbRef)

  def newFutureFetchingScenarioRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[Future](
      dbRef,
      newActionProcessRepository(dbRef),
      newScenarioLabelsRepository(dbRef)
    ) with BasicRepository

  def newFetchingProcessRepository(dbRef: DbRef) =
    new DBFetchingProcessRepository[DB](dbRef, newActionProcessRepository(dbRef), newScenarioLabelsRepository(dbRef))
      with DbioRepository

  def newWriteProcessRepository(
      dbRef: DbRef,
      clock: Clock,
      processingTypes: List[String] = List(Streaming.stringify),
      modelVersions: Option[Int] = Some(1)
  ) =
    new DBProcessRepository(
      dbRef,
      clock,
      newScenarioActivityRepository(dbRef, clock),
      newScenarioLabelsRepository(dbRef),
      mapProcessingTypeDataProvider(modelVersions.map(mv => processingTypes.map(pt => (pt, mv))).toList.flatten: _*),
    )

  def newDummyWriteProcessRepository(): DBProcessRepository =
    newWriteProcessRepository(dummyDbRef, Clock.systemUTC())

  def newScenarioGraphVersionService(dbRef: DbRef, processingTypes: List[String] = List(Streaming.stringify)) = {
    val validator = processValidator(processingTypes)
    new ScenarioGraphVersionService(
      newScenarioGraphVersionRepository(dbRef),
      mapProcessingTypeDataProvider(processingTypes.map(pt => (pt, validator)): _*),
      scenarioResolverByProcessingType(processingTypes),
      newDBIOActionRunner(dbRef)
    )
  }

  def newScenarioGraphVersionRepository(dbRef: DbRef) = new ScenarioGraphVersionRepository(dbRef)

  def newFragmentRepository(dbRef: DbRef): DefaultFragmentRepository =
    new DefaultFragmentRepository(newFutureFetchingScenarioRepository(dbRef))

  def newActionProcessRepository(dbRef: DbRef): ScenarioActionRepository =
    DbScenarioActionRepository.create(dbRef)

  def newDummyActionRepository(): ScenarioActionRepository =
    newActionProcessRepository(dummyDbRef)

  def newScenarioMetadataRepository(dbRef: DbRef) = new ScenarioMetadataRepository(dbRef)

  def newDeploymentRepository(dbRef: DbRef, clock: Clock) = new DeploymentRepository(dbRef, clock)

  def asAdmin(route: RouteWithUser): Route =
    route.securedRouteWithErrorHandling(adminUser())

  def newProcessPreparer(processingType: String = Streaming.stringify): NewProcessPreparer =
    new NewProcessPreparer(
      ProcessTestData.streamingTypeSpecificInitialData,
      FlinkStreamingPropertiesConfig.properties,
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, processingType)
    )

  def newProcessPreparerByProcessingType(
      processingTypes: List[String] = List(Streaming.stringify)
  ): ProcessingTypeDataProvider[NewProcessPreparer, _] =
    mapProcessingTypeDataProvider(processingTypes.map(pt => (pt, newProcessPreparer(pt))): _*)

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
    TestProcessingTypeDataProviderFactory.createWithEmptyCombinedData(
      Map(data: _*).mapValuesNow(ValueWithRestriction.anyUser)
    )
  }

  def newProcessService(
      dbRef: DbRef,
      clock: Clock,
      oldApproachScenarioStatusProvider: ScenarioStatusProvider,
      dmDispatcher: DeploymentManagerDispatcher,
      processingTypes: List[String] = List(Streaming1.stringify)
  ): ProcessService = new DBProcessService(
    oldApproachScenarioStatusProvider,
    new ScenarioStatusPresenter(dmDispatcher),
    newProcessPreparerByProcessingType(processingTypes),
    scenarioParametersServiceProvider(processingTypes),
    processResolverByProcessingType(processingTypes),
    newDBIOActionRunner(dbRef),
    newFutureFetchingScenarioRepository(dbRef),
    newActionProcessRepository(dbRef),
    newWriteProcessRepository(dbRef, clock, processingTypes),
  )

  def newActionInfoService(modelData: ModelData): ActionInfoService =
    new ActionInfoService(
      new ModelDataActionInfoProvider(
        modelData,
        Resource.pure(EngineScenarioCompilationDependencies.empty)
      ),
      processResolver(),
      scenarioResolver()
    )

}
