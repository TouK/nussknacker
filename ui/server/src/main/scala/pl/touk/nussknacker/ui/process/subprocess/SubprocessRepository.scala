package pl.touk.nussknacker.ui.process.subprocess

import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait SubprocessRepository {

  def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails]

  def loadSubprocesses(versions: Map[String, VersionId], category: String): Set[SubprocessDetails]

  def loadSubprocesses(): Set[SubprocessDetails] = loadSubprocesses(Map.empty)

  def get(id: String) : Option[SubprocessDetails] = loadSubprocesses().find(_.canonical.metaData.id == id)

  def get(id: String, version: VersionId) : Option[SubprocessDetails] = loadSubprocesses(Map(id -> version)).find(_.canonical.metaData.id == id)

}

case class SubprocessDetails(canonical: CanonicalProcess, category: String)

class DbSubprocessRepository(db: DbConfig, ec: ExecutionContext) extends SubprocessRepository {
  //TODO: make it return Future?
  override def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails] = {
    Await.result(listSubprocesses(versions, None), 10 seconds)
  }

  override def loadSubprocesses(versions: Map[String, VersionId], category: String): Set[SubprocessDetails] = {
    Await.result(listSubprocesses(versions, Some(category)), 10 seconds)
  }

  import db.driver.api._
  val espTables = new EspTables {
    override implicit val profile: JdbcProfile = db.driver
  }

  import espTables._
  implicit val iec = ec

  //Fetches subprocess in given version if specified, fetches latest version otherwise
  def listSubprocesses(versions: Map[String, VersionId], category: Option[String]) : Future[Set[SubprocessDetails]] = {
    val versionSubprocesses = Future.sequence {
      versions.map { case (subprocessId, subprocessVersion) =>
        fetchSubprocess(subprocessId, subprocessVersion, category)
      }
    }
    val fetchedSubprocesses = for {
      subprocesses <- versionSubprocesses
      latestSubprocesses <- listLatestSubprocesses(category)
    } yield latestSubprocesses.groupBy(_.canonical.metaData.id).mapValues(_.head) ++ subprocesses.groupBy(_.canonical.metaData.id).mapValues(_.head)

    fetchedSubprocesses.map(_.values.toSet)
  }

  private def listLatestSubprocesses(category: Option[String]) : Future[Set[SubprocessDetails]] = {
    val action = for {
      latestProcesses <- processVersionsTableNoJson.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(subprocessesQuery(category))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result
    } yield latestProcesses.map { case ((_, processVersion), process) =>
      createSubprocessDetails(process, processVersion)
    }
    db.run(action).map(_.flatten.toSet)
    db.run(action).map(_.flatten.toSet)
  }

  private def fetchSubprocess(subprocessName: String, version: VersionId, category: Option[String]) : Future[SubprocessDetails] = {
    val action = for {
      subprocessVersion <- processVersionsTable.filter(p => p.id === version)
        .join(subprocessesQueryByName(subprocessName, category))
        .on { case (latestVersion, process) => latestVersion.processId === process.id }
        .result.headOption
    } yield subprocessVersion.flatMap { case (processVersion, process) =>
      createSubprocessDetails(process, processVersion)
    }

    db.run(action).flatMap {
      case Some(subproc) => Future.successful(subproc)
      case None => Future.failed(new Exception(s"Fragment $subprocessName, version: $version not found"))
    }
  }

  private def createSubprocessDetails(process: ProcessEntityData, processVersion: ProcessVersionEntityData): Option[SubprocessDetails] =
    processVersion.json.map { canonical => SubprocessDetails(canonical, process.processCategory) }

  private def subprocessesQuery(category: Option[String]) = {
    val query = processesTable.filter(_.isSubprocess).filter(!_.isArchived)

    category
      .map{cat =>query.filter(p => p.processCategory === cat)}
      .getOrElse(query)
  }

  private def subprocessesQueryByName(subprocessName: String, category: Option[String]) =
    subprocessesQuery(category).filter(_.name === subprocessName)
}


