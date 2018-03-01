package pl.touk.nussknacker.ui.db.entity

import java.sql.Timestamp

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.ui.db.EspTables
import slick.sql.SqlProfile.ColumnOption.NotNull

object ProcessVersionEntity {

  class ProcessVersionEntity(tag: Tag) extends Table[ProcessVersionEntityData](tag, "process_versions") {
    def id = column[Long]("id", NotNull)

    def json = column[Option[String]]("json", O.Length(100 * 1000))

    def mainClass = column[Option[String]]("main_class", O.Length(5000))

    def createDate = column[Timestamp]("create_date", NotNull)

    def user = column[String]("user", NotNull)

    def processId = column[String]("process_id", NotNull)

    def modelVersion = column[Option[Int]]("model_version", NotNull)


    def * = (id, processId, json, mainClass, createDate, user, modelVersion) <> (ProcessVersionEntityData.apply _ tupled, ProcessVersionEntityData.unapply)

    def pk = primaryKey("pk_process_version", (processId, id))

    private def process = foreignKey("process-version-process-fk", processId, EspTables.processesTable)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }
  case class ProcessVersionEntityData(
                                       id: Long,
                                       processId: String,
                                       json: Option[String],
                                       mainClass: Option[String],
                                       createDate: Timestamp,
                                       user: String,
                                       modelVersion: Option[Int]
                                     ) {
    def deploymentData: ProcessDeploymentData = (json, mainClass) match {
      case (Some(j), _) => GraphProcess(j)
      case (None, Some(mc)) => CustomProcess(mc)
      case _ => throw new IllegalStateException(s"Process version has neither json nor mainClass. ${this}")
    }

    def toProcessVersion: ProcessVersion = ProcessVersion(
      versionId = id,
      processId = processId,
      user = user,
      modelVersion = modelVersion
    )
}

}

