package pl.touk.nussknacker.test.utils.domain

import com.typesafe.config.{Config, ConfigObject, ConfigRenderOptions}
import pl.touk.nussknacker.engine.MetaDataInitializer
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{Comment, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.FlinkStreamingPropertiesConfig
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.CreateProcessAction
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}
import slick.dbio.DBIOAction

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private[test] class ScenarioHelper(dbRef: DbRef, clock: Clock, designerConfig: Config)(
    implicit executionContext: ExecutionContext
) extends PatientScalaFutures {

  private implicit val user: LoggedUser = RealLoggedUser("admin", "admin", Map.empty, isAdmin = true)

  private val dbioRunner: DBIOActionRunner = new DBIOActionRunner(dbRef)

  private val actionRepository: ScenarioActionRepository = DbScenarioActionRepository.create(
    dbRef,
    mapProcessingTypeDataProvider(Map("engine-version" -> "0.1"))
  )

  private val scenarioLabelsRepository: ScenarioLabelsRepository = new ScenarioLabelsRepository(dbRef)

  private val writeScenarioRepository: DBProcessRepository = new DBProcessRepository(
    dbRef,
    clock,
    DbScenarioActivityRepository.create(dbRef, clock),
    scenarioLabelsRepository,
    mapProcessingTypeDataProvider(1)
  )

  private val futureFetchingScenarioRepository: DBFetchingProcessRepository[Future] =
    new DBFetchingProcessRepository[Future](dbRef, actionRepository, scenarioLabelsRepository) with BasicRepository

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
      _  <- archiveScenarioF(ProcessIdWithName(id, scenarioName), VersionId(1))
    } yield id).futureValue
  }

  def archiveScenario(idWithName: ProcessIdWithName, version: VersionId = VersionId(1)): Unit =
    archiveScenarioF(idWithName, version).futureValue

  private def archiveScenarioF(idWithName: ProcessIdWithName, version: VersionId): Future[Unit] =
    dbioRunner.runInTransaction(
      DBIOAction.seq(
        writeScenarioRepository.archive(processId = idWithName, isArchived = true),
        actionRepository.addInstantAction(idWithName.id, version, ScenarioActionName.Archive, None, None)
      )
    )

  private def prepareDeploy(scenarioId: ProcessId, processingType: String): Future[_] = {
    val actionName = ScenarioActionName.Deploy
    val comment    = Comment.from("Deploy comment")
    dbioRunner.run(
      actionRepository.addInstantAction(
        scenarioId,
        VersionId.initialVersionId,
        actionName,
        comment,
        Some(processingType)
      )
    )
  }

  private def prepareCancel(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName.Cancel
    val comment    = Comment.from("Cancel comment")
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionName, comment, None)
    )
  }

  private def prepareCustomAction(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName("Custom")
    val comment    = Comment.from("Execute custom action")
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionName, comment, None)
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
      // FIXME: Using method `runInSerializableTransactionWithRetry` is a workaround for problem with flaky tests
      // (some tests failed with [java.sql.SQLTransactionRollbackException: transaction rollback: serialization failure])
      // the underlying cause of that errors needs investigating
      _  <- dbioRunner.runInSerializableTransactionWithRetry(writeScenarioRepository.saveNewProcess(action))
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

  private lazy val processingTypeWithCategories: Set[(String, String)] = {
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
