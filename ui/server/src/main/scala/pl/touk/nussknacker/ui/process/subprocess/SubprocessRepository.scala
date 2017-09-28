package pl.touk.nussknacker.ui.process.subprocess

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.db.EspTables.{processVersionsTable, processesTable}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait SubprocessRepository {

  def loadSubprocesses(versions: Map[String, Long]): Set[CanonicalProcess]

  def loadSubprocesses(): Set[CanonicalProcess] = loadSubprocesses(Map.empty)

  def get(id: String) : Option[CanonicalProcess] = loadSubprocesses().find(_.metaData.id == id)

  def get(id: String, version: Long) : Option[CanonicalProcess] = loadSubprocesses(Map(id -> version)).find(_.metaData.id == id)

}

class SetSubprocessRepository(processes: Set[CanonicalProcess]) extends SubprocessRepository {
  override def loadSubprocesses(versions: Map[String, Long]): Set[CanonicalProcess] = processes
}

class DbSubprocessRepository(db: DbConfig, ec: ExecutionContext) extends SubprocessRepository {
  //TODO: make it return Future?
  override def loadSubprocesses(versions: Map[String, Long]): Set[CanonicalProcess] = Await.result(listSubprocesses(versions), 10 seconds)

  import db.driver.api._

  implicit val iec = ec

  //Fetches subprocess in given version if specified, fetches latest version otherwise
  def listSubprocesses(versions: Map[String, Long]) : Future[Set[CanonicalProcess]] = {
    val versionSubprocesses = Future.sequence {
      versions.map { case (subprocessId, subprocessVersion) =>
        fetchSubprocess(subprocessId, subprocessVersion)
      }
    }
    val fetchedSubprocesses = for {
      subprocesses <- versionSubprocesses
      latestSubprocesses <- listLatestSubprocesses()
    } yield latestSubprocesses.groupBy(_.metaData.id).mapValues(_.head) ++ subprocesses.groupBy(_.metaData.id).mapValues(_.head)

    fetchedSubprocesses.map(_.values.toSet)
  }

  private def listLatestSubprocesses() : Future[Set[CanonicalProcess]] = {
    val action = for {
      latestProcesses <- processVersionsTable.groupBy(_.processId).map { case (n, group) => (n, group.map(_.createDate).max) }
        .join(processVersionsTable).on { case (((processId, latestVersionDate)), processVersion) =>
        processVersion.processId === processId && processVersion.createDate === latestVersionDate
      }.join(processesTable.filter(_.isSubprocess))
        .on { case ((_, latestVersion), process) => latestVersion.processId === process.id }
        .result
    } yield latestProcesses.map { case ((_, processVersion), _) =>
      processVersion.json.map(ProcessConverter.toCanonicalOrDie)
    }
    db.run(action).map(_.flatten.toSet)
  }

  private def fetchSubprocess(subprocessId: String, version: Long) : Future[CanonicalProcess] = {
    val action = for {
      subprocessVersion <- processVersionsTable.filter(p => p.id === version && p.processId === subprocessId).result.headOption
    } yield subprocessVersion.flatMap { case (processVersion) =>
      processVersion.json.map(ProcessConverter.toCanonicalOrDie)
    }
    db.run(action).flatMap {
      case Some(subproc) => Future.successful(subproc)
      case None => Future.failed(new Exception(s"Subprocess ${subprocessId}, version: ${version} not found"))
    }
  }

}


