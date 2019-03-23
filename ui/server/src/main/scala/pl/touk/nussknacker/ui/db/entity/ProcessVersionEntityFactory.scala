package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import slick.jdbc.JdbcProfile
import slick.lifted.{TableQuery => LTableQuery}
import slick.sql.SqlProfile.ColumnOption.NotNull

trait ProcessVersionEntityFactory {

  protected val profile: JdbcProfile
  val processesTable: LTableQuery[ProcessEntityFactory#ProcessEntity]

  import profile.api._

  class ProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {

    def id = column[Long]("id", NotNull)

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def mainClass = column[Option[String]]("main_class", O.Length(5000))

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def processId = column[Long]("process_id", NotNull)

    def modelVersion = column[Option[Int]]("model_version", NotNull)


    def * = (id, processId, json, mainClass, createDate, user, modelVersion) <> (ProcessVersionEntityData.apply _ tupled, ProcessVersionEntityData.unapply)

    def pk = primaryKey("pk_process_version", (processId, id))

    private def process = foreignKey("process-version-process-fk", processId, processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  val processVersionsTable: LTableQuery[ProcessVersionEntityFactory#ProcessVersionEntity] =
    LTableQuery(new ProcessVersionEntity(_))

}

case class ProcessVersionEntityData(id: Long,
                                    processId: Long,
                                    json: Option[String],
                                    mainClass: Option[String],
                                    createDate: Timestamp,
                                    user: String,
                                    modelVersion: Option[Int]) {
  def deploymentData: ProcessDeploymentData = (json, mainClass) match {
    case (Some(j), _) => GraphProcess(j)
    case (None, Some(mc)) => CustomProcess(mc)
    case _ => throw new IllegalStateException(s"Process version has neither json nor mainClass. ${this}")
  }

  def toProcessVersion(processName: ProcessName): ProcessVersion = ProcessVersion(
    versionId = id,
    processName = processName,
    user = user,
    modelVersion = modelVersion
  )
}

