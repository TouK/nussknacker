package pl.touk.nussknacker.ui.db.entity

import db.migration.DefaultJdbcProfile.profile.api._
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import slick.sql.SqlProfile.ColumnOption.NotNull

object ProcessEntity {

  implicit def processTypeMapper = MappedColumnType.base[ProcessType, String](
    _.toString,
    ProcessType.withName
  )

  implicit def processingTypeMapper = MappedColumnType.base[ProcessingType, String](
    _.toString,
    ProcessingType.withName
  )


  class ProcessEntity(tag: Tag) extends Table[ProcessEntityData](tag, "processes") {

    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name", NotNull)

    def description = column[Option[String]]("description", O.Length(1000))

    def processType = column[ProcessType]("type", NotNull)

    def processCategory = column[String]("category", NotNull)

    def processingType = column[ProcessingType]("processing_type", NotNull)

    def isSubprocess = column[Boolean]("is_subprocess", NotNull)

    def * = (id, name, description, processType, processCategory, processingType, isSubprocess) <> (ProcessEntityData.apply _ tupled, ProcessEntityData.unapply)

  }

  case class ProcessEntityData(id: String,
                               name: String,
                               description: Option[String],
                               processType: ProcessType,
                               processCategory: String,
                               processingType: ProcessingType,
                               isSubprocess: Boolean
                              )

  object ProcessType extends Enumeration {
    type ProcessType = Value
    val Graph = Value("graph")
    val Custom = Value("custom")

    def fromDeploymentData(processDeploymentData: ProcessDeploymentData) : ProcessType = processDeploymentData match {
      case _:GraphProcess => ProcessType.Graph
      case _:CustomProcess => ProcessType.Custom
    }

  }

  object ProcessingType extends Enumeration {
    type ProcessingType = Value
    val Streaming = Value("streaming")
    val RequestResponse = Value("request-response")
  }

}

