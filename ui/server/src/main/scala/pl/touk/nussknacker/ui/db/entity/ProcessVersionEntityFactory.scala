package pl.touk.nussknacker.ui.db.entity

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import slick.jdbc.JdbcProfile
import slick.lifted.{ForeignKeyQuery, TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

import java.sql.Timestamp

trait ProcessVersionEntityFactory {

  protected val profile: JdbcProfile
  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  import profile.api._

  class ProcessVersionEntity(tag: Tag) extends BaseProcessVersionEntity(tag) {

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def * = (id, processId, json, createDate, user, modelVersion) <> (
      (ProcessVersionEntityData.createRich _).tupled,
      (e: ProcessVersionEntityData) => ProcessVersionEntityData.unapply(e).map { t => (t._1.value, t._2.value, t._3, t._4, t._5, t._6) }
    )

  }

  class ProcessVersionEntityNoJson(tag: Tag) extends BaseProcessVersionEntity(tag) {

    override def * =  (id, processId, createDate, user, modelVersion) <> (
      (ProcessVersionEntityData.createSimple _).tupled,
      (e: ProcessVersionEntityData) => ProcessVersionEntityData.unapply(e).map { t => (t._1.value, t._2.value, t._4, t._5, t._6) }
    )

  }

  abstract class BaseProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {

    def id: Rep[Long] = column[Long]("id", NotNull)

    def createDate: Rep[Timestamp] = column[Timestamp]("create_date", NotNull)

    def user: Rep[String] = column[String]("user", NotNull)

    def processId: Rep[Long] = column[Long]("process_id", NotNull)

    def modelVersion: Rep[Option[Int]] = column[Option[Int]]("model_version", NotNull)

    def pk = primaryKey("pk_process_version", (processId, id))

    private def process: ForeignKeyQuery[ProcessEntityFactory#ProcessEntity, ProcessEntityData] = foreignKey("process-version-process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  val processVersionsTable: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntity] =
    LTableQuery(new ProcessVersionEntity(_))

  val processVersionsTableNoJson: TableQuery[ProcessVersionEntityFactory#ProcessVersionEntityNoJson] =
    LTableQuery(new ProcessVersionEntityNoJson(_))
}

object ProcessVersionEntityData {

  def createRich(id: Long, processId: Long, json: Option[String], createDate: Timestamp, user: String, modelVersion: Option[Int]) =
    new ProcessVersionEntityData(VersionId(id), ProcessId(processId), json, createDate, user, modelVersion)

  def createSimple(id: Long, processId: Long, createDate: Timestamp, user: String, modelVersion: Option[Int]) =
    new ProcessVersionEntityData(VersionId(id), ProcessId(processId), None, createDate, user, modelVersion)

}

case class ProcessVersionEntityData(id: VersionId,
                                    processId: ProcessId,
                                    json: Option[String],
                                    createDate: Timestamp,
                                    user: String,
                                    modelVersion: Option[Int]) {

  def graphProcess: GraphProcess = json match {
    case Some(j) => GraphProcess(j)
    case _ => throw new IllegalStateException(s"Scenario version has neither json. $this")
  }

  def toProcessVersion(processName: ProcessName): ProcessVersion = ProcessVersion(
    versionId = id,
    processName = processName,
    processId = processId,
    user = user,
    modelVersion = modelVersion
  )
}

