package pl.touk.nussknacker.ui.api.helpers

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.restmodel.{ProcessType, processdetails}
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.repository.{BasicRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import scala.concurrent.{ExecutionContext, Future}

class StubProcessRepository[T: ProcessShapeFetchStrategy](processes: List[BaseProcessDetails[T]])(implicit ec: ExecutionContext)
  extends FetchingProcessRepository[Future] with BasicRepository {

  //It's only for BasicRepository implementation, we don't use it
  private val config: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)
  val dbConfig: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", config), HsqldbProfile)

  private def getProcesses[PS: ProcessShapeFetchStrategy] = Future(processes.asInstanceOf[List[BaseProcessDetails[PS]]])

  private def getUserProcesses[PS: ProcessShapeFetchStrategy](implicit loggedUser: LoggedUser) = getProcesses[PS].map(_.filter(
    p => p.processType == ProcessType.Graph && hasAccess(p, loggedUser)
  ))

  private def hasAccess(p: BaseProcessDetails[_], user: LoggedUser) = user.isAdmin || user.can(p.processCategory, Permission.Read)

  private def check[T](condition: Option[T], value: T) = condition.forall(_ == value)

  private def checkSeq[T](condition: Option[Seq[T]], value: T) = condition.forall(_.contains(value))

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(p => p.idWithName.id == id).lastOption)

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: Long)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.find(p => p.idWithName.id == processId && p.processVersionId == versionId))

  //TODO: Implement
  override def fetchLatestProcessVersion[PS: processdetails.ProcessShapeFetchStrategy](processId: ProcessId)(implicit loggedUser: LoggedUser): Future[Option[ProcessVersionEntityData]] =
    Future(None)

  override def fetchProcesses[PS: processdetails.ProcessShapeFetchStrategy](isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(
      p => check(isSubprocess, p.isSubprocess) && check(isArchived, p.isArchived) && check(isDeployed, p.isDeployed) && checkSeq(categories, p.processCategory) && checkSeq(processingTypes, p.processType)
    ))

  override def fetchProcesses[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS](isSubprocess = Some(false), isArchived = Some(false), isDeployed = None, categories = None, processingTypes = None)

  override def fetchCustomProcesses[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] = getProcesses[PS].map(_.filter(
    p => !p.isArchived && !p.isSubprocess && p.processType == ProcessType.Custom && hasAccess(p, loggedUser)
  ))

  override def fetchProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS]

  override def fetchProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy](processNames: List[ProcessName])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS].map(_.filter(p => processNames.contains(p.idWithName.name)))

  override def fetchDeployedProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS](isSubprocess = Some(false), isArchived = Some(false), isDeployed = Some(true), categories = None, processingTypes = None)

  override def fetchSubProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS](isSubprocess = Some(true), isArchived = Some(false), isDeployed = None, categories = None, processingTypes = None)

  override def fetchAllProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS]

  override def fetchArchivedProcesses[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    fetchProcesses[PS](isSubprocess = None, isArchived = Some(true), isDeployed = None, categories = None, processingTypes = None)

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]] =
    Future(processes.find(p => p.idWithName.name == processName).map(_.processId))

  override def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]] =
    Future(processes.find(p => p.processId == processId).map(_.idWithName.name))

  //TODO: Implement
  override def fetchProcessActions(processId: ProcessId)(implicit ec: ExecutionContext): Future[List[processdetails.ProcessAction]] =
    Future(List.empty)

  override def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[String] =
    Future(processes.find(p => p.processId == processId && hasAccess(p, loggedUser)).map(_.processingType).get)

  //TODO: Implement
  override def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessEntityData]] =
    Future(None)
}
