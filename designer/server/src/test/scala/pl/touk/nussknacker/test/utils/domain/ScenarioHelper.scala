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
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.{StickyNoteAddRequest, StickyNoteCorrelationId}
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.process.NewProcessPreparer
import pl.touk.nussknacker.ui.process.processingtype.ValueWithRestriction
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{
  CreateProcessAction,
  ProcessUpdated,
  UpdateProcessAction
}
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.process.repository.activities.DbScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.stickynotes.{DbStickyNotesRepository, StickyNotesRepository}
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

  private val actionRepository: ScenarioActionRepository = DbScenarioActionRepository.create(dbRef)

  private val scenarioLabelsRepository: ScenarioLabelsRepository = new ScenarioLabelsRepository(dbRef)
  private val stickyNotesRepository: StickyNotesRepository       = DbStickyNotesRepository.create(dbRef, clock)

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

  def updateScenario(
      scenarioName: ProcessName,
      newScenario: CanonicalProcess
  ): ProcessUpdated = {
    updateAndGetScenarioVersions(scenarioName, newScenario).futureValue
  }

  def addStickyNote(
      scenarioName: ProcessName,
      request: StickyNoteAddRequest
  ): StickyNoteCorrelationId = {
    addStickyNoteForScenario(scenarioName, request).futureValue
  }

  def createDeployedExampleScenario(scenarioName: ProcessName, category: String, isFragment: Boolean): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenario: CanonicalProcess,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- Future(createSavedScenario(scenario, category, isFragment))
      _  <- prepareDeploy(id)
    } yield id).futureValue
  }

  def createDeployedCanceledExampleScenario(
      scenarioName: ProcessName,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id)
      _  <- prepareCancel(id)
    } yield id).futureValue
  }

  def createDeployedScenario(
      scenarioName: ProcessName,
      category: String,
      isFragment: Boolean
  ): ProcessId = {
    (for {
      id <- prepareValidScenario(scenarioName, category, isFragment)
      _  <- prepareDeploy(id)
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
        actionRepository.addInstantAction(idWithName.id, version, ScenarioActionName.Archive, None)
      )
    )

  private def prepareDeploy(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName.Deploy
    val comment    = Comment.from("Deploy comment")
    dbioRunner.run(
      actionRepository.addInstantAction(
        scenarioId,
        VersionId.initialVersionId,
        actionName,
        comment,
      )
    )
  }

  private def prepareCancel(scenarioId: ProcessId): Future[_] = {
    val actionName = ScenarioActionName.Cancel
    val comment    = Comment.from("Cancel comment")
    dbioRunner.run(
      actionRepository.addInstantAction(scenarioId, VersionId.initialVersionId, actionName, comment)
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

  private def updateAndGetScenarioVersions(
      scenarioName: ProcessName,
      newScenario: CanonicalProcess
  ): Future[ProcessUpdated] = {
    for {
      scenarioId <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
      action = UpdateProcessAction(
        scenarioId,
        newScenario,
        comment = None,
        labels = List.empty,
        increaseVersionWhenJsonNotChanged = true,
        forwardedUserName = None
      )
      processUpdated <- dbioRunner.runInTransaction(writeScenarioRepository.updateProcess(action))
    } yield processUpdated
  }

  private def addStickyNoteForScenario(
      scenarioName: ProcessName,
      request: StickyNoteAddRequest
  ): Future[StickyNoteCorrelationId] = {
    for {
      scenarioId <- futureFetchingScenarioRepository.fetchProcessId(scenarioName).map(_.get)
      noteCorrelationId <- dbioRunner.runInTransaction(
        stickyNotesRepository.addStickyNote(
          request.content,
          request.layoutData,
          request.color,
          request.dimensions,
          request.targetEdge,
          scenarioId,
          request.scenarioVersionId
        )
      )
    } yield noteCorrelationId
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
