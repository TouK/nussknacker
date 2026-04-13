package pl.touk.nussknacker.ui.process.draft

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, VersionId}
import pl.touk.nussknacker.ui.notifications.Notification
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, ProcessDraftRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessDraftRepository.ScenarioDraft
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.InMemoryTimeseriesRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

object ProcessDraftService {

  @JsonCodec final case class ScenarioDraftDto(
      scenarioGraph: ScenarioGraph,
      baseVersionId: Option[VersionId],
      updatedAt: Instant,
      updatedBy: String,
  )

  @JsonCodec final case class SaveDraftCommand(
      scenarioGraph: ScenarioGraph,
      baseVersionId: Option[VersionId],
  )

  def toDto(draft: ScenarioDraft): ScenarioDraftDto =
    ScenarioDraftDto(
      scenarioGraph = draft.scenarioGraph,
      baseVersionId = draft.baseVersionId,
      updatedAt = draft.updatedAt,
      updatedBy = draft.updatedBy,
    )

}

class ProcessDraftService(
    draftRepository: ProcessDraftRepository,
    dbioRunner: DBIOActionRunner,
    globalNotificationRepository: InMemoryTimeseriesRepository[Notification],
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  import ProcessDraftService._

  def getDraft(processIdWithName: ProcessIdWithName): Future[Option[ScenarioDraftDto]] =
    dbioRunner.run(draftRepository.fetchDraft(processIdWithName.id)).map(_.map(toDto))

  def saveDraft(processIdWithName: ProcessIdWithName, cmd: SaveDraftCommand)(
      implicit user: LoggedUser
  ): Future[ScenarioDraftDto] =
    dbioRunner
      .runInTransaction(draftRepository.upsertDraft(processIdWithName.id, cmd.scenarioGraph, cmd.baseVersionId))
      .map { draft =>
        globalNotificationRepository.saveEntry(Notification.draftUpdated(processIdWithName.name, user.username))
        toDto(draft)
      }

  def deleteDraft(processIdWithName: ProcessIdWithName)(implicit user: LoggedUser): Future[Unit] =
    dbioRunner.runInTransaction(draftRepository.deleteDraft(processIdWithName.id)).map { _ =>
      globalNotificationRepository.saveEntry(Notification.draftDiscarded(processIdWithName.name, user.username))
    }

}
