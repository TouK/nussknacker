package pl.touk.nussknacker.ui.db

import pl.touk.nussknacker.ui.db.entity.AttachmentEntity.AttachmentEntity
import pl.touk.nussknacker.ui.db.entity.CommentEntity.CommentEntity
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.ProcessDeploymentInfoEntity
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntity.EnvironmentsEntity
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessEntity
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntity
import pl.touk.nussknacker.ui.db.entity.TagsEntity.TagsEntity
import slick.lifted.TableQuery

object EspTables {
  val processesTable = TableQuery[ProcessEntity]
  val processVersionsTable = TableQuery[ProcessVersionEntity]
  val deployedProcessesTable = TableQuery[ProcessDeploymentInfoEntity]
  val tagsTable = TableQuery[TagsEntity]
  val environmentsTable = TableQuery[EnvironmentsEntity]
  val commentsTable = TableQuery[CommentEntity]
  val attachmentsTable = TableQuery[AttachmentEntity]
}
