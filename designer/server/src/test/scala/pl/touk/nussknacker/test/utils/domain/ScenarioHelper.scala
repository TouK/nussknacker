package pl.touk.nussknacker.test.utils.domain

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions}
import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeDataProvider, ValueWithRestriction}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private[test] class ScenarioHelper(dbRef: DbRef, designerConfig: Config)(implicit executionContext: ExecutionContext)
    extends PatientScalaFutures {

  private implicit val user: LoggedUser = RealLoggedUser("admin", "admin", Map.empty, isAdmin = true)

  private val dbioRunner: DBIOActionRunner = new DBIOActionRunner(dbRef)

  private val actionRepository: DbProcessActionRepository = new DbProcessActionRepository(
    dbRef,
    mapProcessingTypeDataProvider(Map("engine-version" -> "0.1"))
  ) with DbioRepository

  private val writeScenarioRepository: DBProcessRepository = new DBProcessRepository(
    dbRef,
    mapProcessingTypeDataProvider(1)
  )

  private val futureFetchingScenarioRepository: DBFetchingProcessRepository[Future] =
    new DBFetchingProcessRepository[Future](dbRef, actionRepository) with BasicRepository

  def createEmptyScenario(scenarioName: ProcessName, category: String, isFragment: Boolean): ProcessId = {
    val newProcessPreparer: NewProcessPreparer = new NewProcessPreparer(
      MetaDataInitializer(
        StreamMetaData.typeName,
        Map(StreamMetaData.parallelismName -> "1", StreamMetaData.spillStateToDiskName -> "true")
      ),
      FlinkStreamingPropertiesConfig.properties,
      new ScenarioPropertiesConfigFinalizer(TestAdditionalUIConfigProvider, Streaming.stringify)
    )
    createSavedScenario(
      newProcessPreparer.prepareEmptyProcess(scenarioName, isFragment = false),
      category,
      isFragment
    )
  }

  def createSavedScenario(
      scenario: CanonicalProcess,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    saveAndGetId(scenario, category, isFragment).futureValue
  }

  def createDeployedExampleScenario(scenarioName: ProcessName, category: String, isFragment: Boolean): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id, processingTypeBy(category))
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenario: CanonicalProcess,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- Future(createSavedScenario(scenario, category, isFragment))
      _  <- prepareDeploy(id, processingTypeBy(category))
    } yield id).futureValue
  }

  def createDeployedCanceledExampleScenario(
      scenarioName: ProcessName,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id, processingTypeBy(category))
      _  <- prepareCancel(id)
    } yield id).futureValue
  }

  def createDeployedWithCustomActionScenario(
      scenarioName: ProcessName,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id, processingTypeBy(category))
      _  <- prepareCustomAction(id)
    } yield id).futureValue
  }

  def createArchivedExampleScenario(scenarioName: ProcessName, category: String, isFragment: Boolean): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _ <- dbioRunner.runInTransaction(
        DBIOAction.seq(
          writeScenarioRepository.archive(processId = ProcessIdWithName(id, scenarioName), isArchived = true),
          actionRepository.markProcessAsArchived(processId = id, VersionId(1))
        )
      )
    } yield id).futureValue
  }

  private def prepareDeploy(scenarioId: ProcessId, processingType: String): Future[_] = {
    val actionName = ScenarioActionName.Deploy
    val comment    = DeploymentComment.unsafe(UserComment("Deploy comment")).toComment(actionName)
    dbioRunner.run(
      actionRepository.addInstantAction(
        scenarioId,
        VersionId.initialVersionId,
        actionName,
        Some(comment),
        Some(processingType)
      )
    )
  }

  private def prepareCancel(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName.Cancel
    val comment    = DeploymentComment.unsafe(UserComment("Cancel comment")).toComment(actionName)
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionName, Some(comment), None)
    )
  }

  private def prepareCustomAction(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName("Custom")
    val comment    = DeploymentComment.unsafe(UserComment("Execute custom action")).toComment(actionName)
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionName, Some(comment), None)
    )
  }

  private def prepareValidScenario(
      scenarioName: ProcessName,
      category: String,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val validScenario = ProcessTestData.sampleScenario
    val withNameSet   = validScenario.withProcessName(scenarioName)
    saveAndGetId(withNameSet, category, isFragment)
  }

  private def saveAndGetId(
      scenario: CanonicalProcess,
      category: String,
      isFragment: Boolean
  ): Future[ProcessId] = {
    val scenarioName = scenario.name
    val action = CreateProcessAction(
      scenarioName,
      category,
      scenario,
      processingTypeBy(category),
      isFragment,
      forwardedUserName = None
    )
    for {
      _  <- dbioRunner.runInTransaction(writeScenarioRepository.saveNewProcess(action))
      id <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
    } yield id
  }

  private def mapProcessingTypeDataProvider[T](value: T) = {
    ProcessingTypeDataProvider.withEmptyCombinedData(
      processingTypeWithCategories.map { case (processingType, _) =>
        (processingType, ValueWithRestriction.anyUser(value))
      }.toMap
    )
  }

  private def processingTypeBy(category: String) = {
    processingTypeWithCategories
      .map(_.swap)
      .toMap
      .getOrElse(
        category,
        throw new IllegalArgumentException(
          s"Cannot find processing type for category $category in the following config:\n${designerConfig.root().render(ConfigRenderOptions.defaults().setFormatted(true))}"
        )
      )
  }

  private lazy val processingTypeWithCategories = {
    val scenarioTypeConfigObject = designerConfig.getObject("scenarioTypes")
    val processingTypes          = scenarioTypeConfigObject.keySet().asScala.toSet

    val processingTypesWithTheirCategories = processingTypes.map { processingType =>
      val categoryOfTheProcessingType = scenarioTypeConfigObject
        .get(processingType)
        .asInstanceOf[ConfigObject]
        .get("category")
        .unwrapped()
        .asInstanceOf[String]
      (processingType, categoryOfTheProcessingType)
    }
    processingTypesWithTheirCategories
  }

}
