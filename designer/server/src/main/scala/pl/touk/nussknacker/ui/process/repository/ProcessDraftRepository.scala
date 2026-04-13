package pl.touk.nussknacker.ui.process.repository

import com.typesafe.scalalogging.LazyLogging
import db.util.DBIOActionInstances._
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.db.DbRef
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.ui.db.NuTables
import pl.touk.nussknacker.ui.db.entity.ProcessDraftEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessDraftRepository.ScenarioDraft
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.sql.Timestamp
import java.time.Instant
import scala.concurrent.ExecutionContext

trait ProcessDraftRepository {

  def upsertDraft(
      processId: ProcessId,
      scenarioGraph: ScenarioGraph,
      baseVersionId: Option[VersionId],
  )(implicit user: LoggedUser): DB[ScenarioDraft]

  def fetchDraft(processId: ProcessId): DB[Option[ScenarioDraft]]

  def deleteDraft(processId: ProcessId): DB[Unit]

}

object ProcessDraftRepository {

  final case class ScenarioDraft(
      processId: ProcessId,
      scenarioGraph: ScenarioGraph,
      baseVersionId: Option[VersionId],
      updatedAt: Instant,
      updatedBy: String,
  )

}

class DbProcessDraftRepository(override protected val dbRef: DbRef)(
    implicit executionContext: ExecutionContext,
) extends DbioRepository
    with NuTables
    with ProcessDraftRepository
    with LazyLogging {

  import profile.apiWithEnforcedSchema._

  override def upsertDraft(
      processId: ProcessId,
      scenarioGraph: ScenarioGraph,
      baseVersionId: Option[VersionId],
  )(implicit user: LoggedUser): DB[ScenarioDraft] = {
    val now = Instant.now()
    val entity = ProcessDraftEntityData(
      processId = processId,
      scenarioGraph = scenarioGraph.asJson.noSpaces,
      baseVersionId = baseVersionId,
      updatedAt = Timestamp.from(now),
      updatedBy = user.username,
    )
    processDraftsTable.insertOrUpdate(entity).map { _ =>
      ScenarioDraft(
        processId = processId,
        scenarioGraph = scenarioGraph,
        baseVersionId = baseVersionId,
        updatedAt = now,
        updatedBy = user.username,
      )
    }
  }

  override def fetchDraft(processId: ProcessId): DB[Option[ScenarioDraft]] =
    processDraftsTable
      .filter(_.processId === processId)
      .result
      .headOption
      .map(_.flatMap(toScenarioDraft))

  override def deleteDraft(processId: ProcessId): DB[Unit] =
    processDraftsTable.filter(_.processId === processId).delete.map(_ => ())

  private def toScenarioDraft(entity: ProcessDraftEntityData): Option[ScenarioDraft] =
    io.circe.parser.decode[ScenarioGraph](entity.scenarioGraph) match {
      case Right(graph) =>
        Some(
          ScenarioDraft(
            processId = entity.processId,
            scenarioGraph = graph,
            baseVersionId = entity.baseVersionId,
            updatedAt = entity.updatedAt.toInstant,
            updatedBy = entity.updatedBy,
          )
        )
      case Left(error) =>
        logger.warn(s"Failed to decode draft scenario graph for process ${entity.processId}: ${error.getMessage}")
        None
    }

}
