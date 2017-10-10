package pl.touk.nussknacker.ui.process.repository

import java.time.LocalDateTime

import argonaut.CodecJson
import pl.touk.nussknacker.ui.app.BuildInfo
import pl.touk.nussknacker.ui.db.EspTables._
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.DeployedProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.entity.{ProcessEntity, ProcessVersionEntity}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.DateUtils
import pl.touk.nussknacker.ui.{BadRequestError, NotFoundError}

import scala.language.higherKinds

trait ProcessRepository[F[_]] extends Repository[F] {

  import api._

  protected def processTableFilteredByUser(implicit loggedUser: LoggedUser): Query[ProcessEntity.ProcessEntity, ProcessEntityData, Seq] = {
    if (loggedUser.isAdmin) processesTable else processesTable.filter(_.processCategory inSet loggedUser.categories)
  }

  protected def latestProcessVersions(processId: String): Query[ProcessVersionEntity.ProcessVersionEntity, ProcessVersionEntityData, Seq] = {
    processVersionsTable.filter(_.processId === processId).sortBy(_.createDate.desc)
  }


}

object ProcessRepository {

  case class BaseProcessDetails[ProcessShape](
                             id: String,
                             name: String,
                             processVersionId: Long,
                             isLatestVersion: Boolean,
                             description: Option[String],
                             processType: ProcessType,
                             processingType: ProcessingType,
                             processCategory: String,
                             modificationDate: LocalDateTime,
                             tags: List[String],
                             currentlyDeployedAt: Set[String],
                             json: Option[ProcessShape],
                             history: List[ProcessHistoryEntry],
                             modelVersion: Option[Int]
                           ) {
    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = json.map(action))
  }

  type ProcessDetails = BaseProcessDetails[DisplayableProcess]

  type ValidatedProcessDetails = BaseProcessDetails[ValidatedDisplayableProcess]


  case class ProcessHistoryEntry(processId: String,
                                 processName: String,
                                 processVersionId: Long,
                                 createDate: LocalDateTime,
                                 user: String,
                                 deployments: List[DeploymentEntry]
                                )
  object ProcessHistoryEntry {
    def apply(process: ProcessEntityData,
              processVersion: ProcessVersionEntityData,
              deployedVersionsPerEnv: Map[String, DeployedProcessVersionEntityData]): ProcessHistoryEntry = {
      new ProcessHistoryEntry(
        processId = process.id,
        processVersionId = processVersion.id,
        processName = process.name,
        createDate = DateUtils.toLocalDateTime(processVersion.createDate),
        user = processVersion.user,
        deployments = deployedVersionsPerEnv.collect { case (env, deployedVersion) if deployedVersion.processVersionId.contains(processVersion.id) =>
          DeploymentEntry(env, deployedVersion.deployedAtTime,
            deployedVersion.buildInfo.flatMap(BuildInfo.parseJson).getOrElse(BuildInfo.empty))
        }.toList
      )
    }
  }

  case class DeploymentEntry(environment: String, deployedAt: LocalDateTime, buildInfo: Map[String, String])

  case class ProcessNotFoundError(id: String) extends Exception(s"No process $id found") with NotFoundError

  case class ProcessAlreadyExists(id: String) extends BadRequestError {
    def getMessage = s"Process $id already exists"
  }


  case class InvalidProcessTypeError(id: String) extends BadRequestError {
    def getMessage = s"Process $id is not GraphProcess"
  }

}