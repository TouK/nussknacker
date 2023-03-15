package pl.touk.nussknacker.ui.process.subprocess

import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.db.{DbConfig, EspTables}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// FIXME: why everywhere pass Map.empty as a version - looks like it can be cleaned
// FIXME: Map[id->process] instead of Sets to avoid unnecessary hashcode computation overhead and easier usage
trait SubprocessRepository {

  def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails]

  def loadSubprocesses(versions: Map[String, VersionId], category: String): Set[SubprocessDetails]

  // FIXME: get by id in DB
  def get(id: String): Option[SubprocessDetails] = loadSubprocesses().find(_.canonical.metaData.id == id)

  // FIXME: load only ids from DB
  def loadSubprocessIds(): List[String] = loadSubprocesses(Map.empty).map(_.canonical.metaData.id).toList

  def loadSubprocesses(): Set[SubprocessDetails] = loadSubprocesses(Map.empty)

}

case class SubprocessDetails(canonical: CanonicalProcess, category: String)

class DbSubprocessRepository(db: DbConfig, ec: ExecutionContext) extends SubprocessRepository {

  private val dbioRunner = DBIOActionRunner(db)

  //TODO: make it return Future?
  override def loadSubprocesses(versions: Map[String, VersionId]): Set[SubprocessDetails] = {
    Await.result(listSubprocesses(versions, None), 10 seconds)
  }

  override def loadSubprocesses(versions: Map[String, VersionId], category: String): Set[SubprocessDetails] = {
    Await.result(listSubprocesses(versions, Some(category)), 10 seconds)
  }

  import db.profile.api._
  val espTables = new EspTables {
    override implicit val profile: JdbcProfile = db.profile
  }

  import espTables._
  implicit val iec = ec

  //Fetches subprocess in given version if specified, fetches latest version otherwise
  def listSubprocesses(versions: Map[String, VersionId], category: Option[String]) : Future[Set[SubprocessDetails]] = {
    val versionSubprocesses = Future.sequence {
      versions.map { case (subprocessId, subprocessVersion) =>
        fetchSubprocess(ProcessName(subprocessId), subprocessVersion, category)
      }
    }
    val fetchedSubprocesses = for {
      subprocesses <- versionSubprocesses
      latestSubprocesses <- listLatestSubprocesses(category)
    } yield latestSubprocesses.groupBy(_.canonical.metaData.id).mapValuesNow(_.head) ++ subprocesses.groupBy(_.canonical.metaData.id).mapValuesNow(_.head)

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
    dbioRunner.run(action).map(_.flatten.toSet)
    dbioRunner.run(action).map(_.flatten.toSet)
  }

  private def fetchSubprocess(subprocessName: ProcessName, version: VersionId, category: Option[String]) : Future[SubprocessDetails] = {
    val action = for {
      subprocessVersion <- processVersionsTable.filter(p => p.id === version)
        .join(subprocessesQueryByName(subprocessName, category))
        .on { case (latestVersion, process) => latestVersion.processId === process.id }
        .result.headOption
    } yield subprocessVersion.flatMap { case (processVersion, process) =>
      createSubprocessDetails(process, processVersion)
    }

    dbioRunner.run(action).flatMap {
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

  private def subprocessesQueryByName(subprocessName: ProcessName, category: Option[String]) =
    subprocessesQuery(category).filter(_.name === subprocessName)
}


