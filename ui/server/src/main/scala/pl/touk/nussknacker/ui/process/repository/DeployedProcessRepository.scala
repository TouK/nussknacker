package pl.touk.nussknacker.ui.process.repository

import java.sql.Timestamp
import java.time.LocalDateTime

import db.util.DBIOActionInstances.DB
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.processdetails.ProcessAction
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.entity.{CommentActions, ProcessActionEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

object DeployedProcessRepository {
  def create(dbConfig: DbConfig, modelData: Map[ProcessingType, ModelData]): DeployedProcessRepository = {
    new DeployedProcessRepository(dbConfig, modelData.mapValues(_.configCreator.buildInfo()))
  }
}

class DeployedProcessRepository(val dbConfig: DbConfig,
                                buildInfos: Map[ProcessingType, Map[String, String]])
  extends BasicRepository with EspTables with CommentActions {

  import profile.api._

  def markProcessAsDeployed(processId: ProcessId, processVersion: Long, processingType: ProcessingType,
                            environment: String, comment: Option[String])
                           (implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessActionEntityData]
  = action(processId, processVersion, environment, comment, ProcessActionType.Deploy,
    buildInfos.get(processingType).map(BuildInfo.writeAsJson))


  def markProcessAsCancelled(processId: ProcessId, processVersion: Long, environment: String, comment: Option[String])
                            (implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessActionEntityData]
  = action(processId, processVersion, environment, comment, ProcessActionType.Cancel, None)

  private def action(processId: ProcessId, processVersion: Long, environment: String,
                     comment: Option[String], action: ProcessActionType.Value, buildInfo: Option[String])
                    (implicit ec: ExecutionContext, user: LoggedUser): Future[ProcessActionEntityData] = {
    val actionToRun = for {
      commentId <- withComment(processId, processVersion, comment)
      processActionData = ProcessActionEntityData(
        processId = processId.value,
        processVersionId = processVersion,
        user = user.username,
        deployedAt = Timestamp.valueOf(LocalDateTime.now()),
        action = action,
        commentId = commentId,
        buildInfo = buildInfo,
        environment = "test"
      )
      _ <- processActionsTable += processActionData
    } yield processActionData
    run(actionToRun)
  }

  private def withComment(processId: ProcessId, processVersion: Long, comment: Option[String])
                         (implicit ec: ExecutionContext, user: LoggedUser): DB[Option[Long]] = comment match {
    case None => DBIOAction.successful(None)
    case Some(comm) => newCommentAction(processId, processVersion, comm)
  }
}
