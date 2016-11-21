package pl.touk.esp.ui.db.migration

import argonaut.PrettyParams
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.entity.DeployedProcessVersionEntity.DeployedProcessVersionEntityData
import pl.touk.esp.ui.db.entity.EnvironmentsEntity.EnvironmentsEntityData
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessEntityData, ProcessType}
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.db.entity.TagsEntity.TagsEntityData
import pl.touk.esp.ui.process.marshall.UiProcessMarshaller
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.util.DateUtils

object SampleData {

  def process = {
    val process = SampleProcess.process
    ProcessEntityData(
      process.id,
      process.id,
      Some("Sample process description"),
      ProcessType.Graph, "Category1"
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
      1,
      "test",
      "TouK",
      DateUtils.now
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

}
