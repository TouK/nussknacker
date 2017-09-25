package pl.touk.nussknacker.ui.process.subprocess

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.db.EspTables.{processVersionsTable, processesTable}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import slick.jdbc.{JdbcBackend, JdbcProfile}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


trait SubprocessRepository {

  def loadSubprocesses(): Set[CanonicalProcess]

  def get(id: String) : Option[CanonicalProcess] = loadSubprocesses().find(_.metaData.id == id)

}

class SetSubprocessRepository(processes: Set[CanonicalProcess]) extends SubprocessRepository {
  override def loadSubprocesses(): Set[CanonicalProcess] = processes
}

class DbSubprocessRepository(db: JdbcBackend.Database,
                             driver: JdbcProfile, ec: ExecutionContext) extends SubprocessRepository {
  //TODO: make it return Future?
  override def loadSubprocesses(): Set[CanonicalProcess] = Await.result(listSubprocesses(), 10 seconds)

  import driver.api._

  implicit val iec = ec

  def listSubprocesses() : Future[Set[CanonicalProcess]] = {
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

}


