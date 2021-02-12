package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime
import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentActions, ProcessActionEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

trait ProcessActionRepository[F[_]] {
  def markProcessAsArchived(processId: ProcessId, processVersion: Long)(implicit ec: ExecutionContext, user: LoggedUser): F[_]
  def markProcessAsUnArchived(processId: ProcessId, processVersion: Long)(implicit ec: ExecutionContext, user: LoggedUser): F[_]
}

object DbProcessActionRepository {
  def create(dbConfig: DbConfig, modelData: ProcessingTypeDataProvider[ModelData]): DbProcessActionRepository =
    new DbProcessActionRepository(dbConfig, modelData.mapValues(_.configCreator.buildInfo()))
}

class DbProcessActionRepository(val dbConfig: DbConfig, buildInfos: ProcessingTypeDataProvider[Map[String, String]])
  extends BasicRepository with EspTables with CommentActions with ProcessActionRepository[DB] {

  import profile.api._

  def markProcessAsDeployed(processId: ProcessId, processVersion: Long, processingType: ProcessingType, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessActionEntityData] =
    //TODO: remove Deployment: after adding custom icons
    run(action(processId, processVersion, comment.map("Deployment: " + _), ProcessActionType.Deploy, buildInfos.forType(processingType).map(BuildInfo.writeAsJson)))

  def markProcessAsCancelled(processId: ProcessId, processVersion: Long, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessActionEntityData] =
    //TODO: remove Stop: after adding custom icons
    run(action(processId, processVersion, comment.map("Stop: " + _), ProcessActionType.Cancel, None))

  override def markProcessAsArchived(processId: ProcessId, processVersion: Long)(implicit ec: ExecutionContext, user: LoggedUser): DB[ProcessActionEntityData] =
    action(processId, processVersion, None, ProcessActionType.Archive, None)

  override def markProcessAsUnArchived(processId: ProcessId, processVersion: Long)(implicit ec: ExecutionContext, user: LoggedUser): DB[ProcessActionEntityData] =
    action(processId, processVersion, None, ProcessActionType.UnArchive, None)

  private def action(processId: ProcessId, processVersion: Long, comment: Option[String], action: ProcessActionType, buildInfo: Option[String])(implicit ec: ExecutionContext, user: LoggedUser) =
    for {
      commentId <- withComment(processId, processVersion, comment)
      processActionData = ProcessActionEntityData(
        processId = processId.value,
        processVersionId = processVersion,
        user = user.username,
        performedAt = Timestamp.valueOf(LocalDateTime.now()),
        action = action,
        commentId = commentId,
        buildInfo = buildInfo
      )
      _ <- processActionsTable += processActionData
    } yield processActionData

  private def withComment(processId: ProcessId, processVersion: Long, comment: Option[String])(implicit ec: ExecutionContext, user: LoggedUser): DB[Option[Long]] = comment match {
    case None => DBIOAction.successful(None)
    case Some(comm) => newCommentAction(processId, processVersion, comm)
  }
}
