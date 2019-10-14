package pl.touk.nussknacker.restmodel.db.entity

import java.sql.Timestamp

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.engine.api.process.ProcessName

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
