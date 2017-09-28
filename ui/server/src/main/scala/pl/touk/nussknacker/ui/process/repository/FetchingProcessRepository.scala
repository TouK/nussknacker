package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{ProcessDetails, ProcessNotFoundError}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

trait FetchingProcessRepository {

  def fetchLatestProcessDetailsForProcessId(id: String, businessView: Boolean = false)
                                                  (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[ProcessDetails]]

  def fetchLatestProcessDetailsForProcessIdEither(id: String, businessView: Boolean = false)
                                                 (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[XError[ProcessDetails]] = {
    fetchLatestProcessDetailsForProcessId(id).map[XError[ProcessDetails]] {
      case None => Left(ProcessNotFoundError(id))
      case Some(p) => Right(p)
    }
  }

  def fetchProcessDetailsForId(processId: String, versionId: Long, businessView: Boolean)
                                (implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[ProcessDetails]]

  def fetchLatestProcessVersion(processId: String)
                               (implicit loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]]

  def fetchProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

  def fetchSubProcessesDetails()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[ProcessDetails]]

}
