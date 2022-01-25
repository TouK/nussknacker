package pl.touk.nussknacker.ui.api.helpers

import cats.instances.future._
import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessShapeFetchStrategy.{FetchCanonical, FetchDisplayable, NotFetch}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.restmodel.processdetails
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.{BasicRepository, FetchingProcessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import slick.jdbc.{HsqldbProfile, JdbcBackend}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds

class MockFetchingProcessRepository(processes: List[BaseProcessDetails[_]])(implicit ec: ExecutionContext) extends FetchingProcessRepository[Future] with BasicRepository {

  //It's only for BasicRepository implementation, we don't use it
  private val config: Config = ConfigFactory.parseString("""db {url: "jdbc:hsqldb:mem:none"}""".stripMargin)
  val dbConfig: DbConfig = DbConfig(JdbcBackend.Database.forConfig("db", config), HsqldbProfile)

  override def fetchProcesses[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isSubprocess = Some(false), isArchived = Some(false))

  override def fetchProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isSubprocess = Some(false), isArchived = Some(false))

  override def fetchDeployedProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isSubprocess = Some(false), isArchived = Some(false), isDeployed = Some(true))

  override def fetchProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy](processNames: List[ProcessName])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isSubprocess = Some(false), isArchived = Some(false)).map(_.filter(p => processNames.contains(p.idWithName.name)))

  override def fetchSubProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isSubprocess = Some(true), isArchived = Some(false))

  override def fetchAllProcessesDetails[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isArchived = Some(false))

  override def fetchArchivedProcesses[PS: processdetails.ProcessShapeFetchStrategy]()(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    filterProcesses[PS](isArchived = Some(true))

  override def fetchLatestProcessDetailsForProcessId[PS: ProcessShapeFetchStrategy](id: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(p => p.idWithName.id == id).lastOption)

  override def fetchProcessDetailsForId[PS: ProcessShapeFetchStrategy](processId: ProcessId, versionId: VersionId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[Option[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.find(p => p.idWithName.id == processId && p.processVersionId == versionId))

  override def fetchProcessId(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessId]] =
    Future(processes.find(p => p.idWithName.name == processName).map(_.processId))

  override def fetchProcessName(processId: ProcessId)(implicit ec: ExecutionContext): Future[Option[ProcessName]] =
    Future(processes.find(p => p.processId == processId).map(_.idWithName.name))

  override def fetchProcessingType(processId: ProcessId)(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[String] =
    getUserProcesses[Unit].map(_.find(p => p.processId == processId).map(_.processingType).get)

  override def fetchProcesses[PS: processdetails.ProcessShapeFetchStrategy](isSubprocess: Option[Boolean], isArchived: Option[Boolean], isDeployed: Option[Boolean], categories: Option[Seq[String]], processingTypes: Option[Seq[String]])(implicit loggedUser: LoggedUser, ec: ExecutionContext): Future[List[processdetails.BaseProcessDetails[PS]]] =
    getUserProcesses[PS].map(_.filter(
      p => check(isSubprocess, p.isSubprocess) && check(isArchived, p.isArchived) && check(isDeployed, p.isDeployed) && checkSeq(categories, p.processCategory) && checkSeq(processingTypes, p.processingType)
    ))

  //TODO: Implement
  override def fetchProcessActions(processId: ProcessId)(implicit ec: ExecutionContext): Future[List[processdetails.ProcessAction]] = ???

  //TODO: Implement
  override def fetchProcessDetails(processName: ProcessName)(implicit ec: ExecutionContext): Future[Option[ProcessEntityData]] = ???

  private def filterProcesses[PS: processdetails.ProcessShapeFetchStrategy](isSubprocess: Option[Boolean] = None, isArchived: Option[Boolean] = None, isDeployed: Option[Boolean] = None, categories: Option[Seq[String]] = None, processingTypes: Option[Seq[String]] = None)(implicit loggedUser: LoggedUser, ec: ExecutionContext) = {
    getUserProcesses[PS].map(_.filter(p => {
      check(isSubprocess, p.isSubprocess) && check(isArchived, p.isArchived) && check(isDeployed, p.isDeployed) && checkSeq(categories, p.processCategory) && checkSeq(processingTypes, p.processingType)
    }))
  }

  private def getUserProcesses[PS: ProcessShapeFetchStrategy](implicit loggedUser: LoggedUser) = getProcesses[PS].map(_.filter(p =>
    loggedUser.isAdmin || loggedUser.can(p.processCategory, Permission.Read)
  ))

  private def getProcesses[PS: ProcessShapeFetchStrategy]: Future[List[BaseProcessDetails[PS]]] = {
    val shapeStrategy: ProcessShapeFetchStrategy[PS] = implicitly[ProcessShapeFetchStrategy[PS]]
    Future(processes.map(p => convertProcess(p)(shapeStrategy)))
  }

  private def convertProcess[PS: ProcessShapeFetchStrategy](process: BaseProcessDetails[_]): BaseProcessDetails[PS] = {
    val shapeStrategy: ProcessShapeFetchStrategy[PS] = implicitly[ProcessShapeFetchStrategy[PS]]

    shapeStrategy match {
      case NotFetch => process.copy(json = Unit.asInstanceOf[PS])
      case FetchDisplayable => process.json match {
        case j: CanonicalProcess => process.copy(json = ProcessConverter.toDisplayable(j, process.processingType))
        case _ => process.asInstanceOf[BaseProcessDetails[PS]]
      }
      case FetchCanonical => process.json match {
        case j: DisplayableProcess => process.copy(json = ProcessConverter.fromDisplayable(j))
        case _ => process.asInstanceOf[BaseProcessDetails[PS]]
      }
      case _ => process.asInstanceOf[BaseProcessDetails[PS]]
    }
  }

  private def check[T](condition: Option[T], value: T) = condition.forall(_ == value)

  private def checkSeq[T](condition: Option[Seq[T]], value: T) = condition.forall(_.contains(value))
}
