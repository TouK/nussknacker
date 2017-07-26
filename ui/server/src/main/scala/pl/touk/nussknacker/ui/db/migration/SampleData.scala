package pl.touk.nussknacker.ui.db.migration

import java.sql.Timestamp
import java.time.LocalDateTime

import argonaut.PrettyParams
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.db.entity.CommentEntity.CommentEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessDeploymentInfoEntity.{DeployedProcessVersionEntityData, DeploymentAction}
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.{ProcessEntityData, ProcessType, ProcessingType}
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.util.DateUtils

object SampleData {

  def process = {
    val process = SampleProcess.process
    ProcessEntityData(
      process.id,
      process.id,
      Some("Sample process description"),
      ProcessType.Graph, "Category1",
      ProcessingType.Streaming,
      isSubprocess = false
    )
  }

  def processVersion = {
    val process = SampleProcess.process
    val json = UiProcessMarshaller().toJson(process, PrettyParams.nospace)
    ProcessVersionEntityData(
      1,
      process.id,
      Some(json),
      None,
      DateUtils.now,
      "TouK"
    )
  }

  def deployedProcess = {
    val process = SampleProcess.process
    DeployedProcessVersionEntityData(
      process.id,
      Some(1),
      "test",
      "TouK",
      DateUtils.now,
      DeploymentAction.Deploy,
      None
    )
  }

  def cancelledProcess = {
    val process = SampleProcess.process
    DeployedProcessVersionEntityData(
      process.id,
      None,
      "test",
      "TouK",
      DateUtils.now,
      DeploymentAction.Cancel,
      None
    )
  }

  def environment = {
    EnvironmentsEntityData("test")
  }

  def tags = {
    val process = SampleProcess.process
    Seq(
      TagsEntityData(
        name = "on_prod",
        processId = process.id
      ),
      TagsEntityData(
        name = "fraud",
        processId = process.id
      )
    )
  }

  def comments = {
    val someDate = LocalDateTime.of(2016, 10, 12, 14, 15, 42)
    val process = SampleProcess.process
    Seq(
      CommentEntityData(-1L, process.id, 1L, "Test message", "TouK", Timestamp.valueOf(someDate.minusHours(1))),
      CommentEntityData(-2L, process.id, 1L, "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec placerat, nunc sed finibus hendrerit, lorem nulla tristique sapien, at porta sem quam et orci. Donec venenatis sagittis ligula, a ultrices justo porta at. Nam accumsan ex quis mattis pulvinar. Maecenas in egestas nulla, ac interdum diam. Nam purus risus, vehicula id dolor a, volutpat ullamcorper nisi. Nulla venenatis malesuada ante eget porta. Mauris non luctus metus, porta laoreet orci. Quisque luctus euismod dolor, at mollis tortor elementum consectetur.", "TouK", Timestamp.valueOf(someDate)),
      CommentEntityData(-3L, process.id, 1L, "Another test message", "TouK", Timestamp.valueOf(someDate.minusHours(2)))
    )
  }

}
