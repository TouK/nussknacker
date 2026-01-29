package pl.touk.nussknacker.engine.aws.managedflink

import io.circe.{parser, Json}
import io.circe.syntax.EncoderOps
import org.apache.commons.codec.binary.Hex
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object AwsManagedFlinkDeploymentPropertiesProvider {

  final case class DeploymentProperties(s3Key: String, content: Array[Byte])

  def buildDeploymentProperties(
      scenario: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      modelConfig: BaseModelData
  ): DeploymentProperties = {
    val modelConfigJson = parser
      .parse(modelConfig.inputConfigDuringExecution.serialized)
      .getOrElse(throw new IllegalStateException("Could not parse modelConfig"))
    val contentJson = Json.obj(
      "scenario"       -> scenario.asJson,
      "version"        -> processVersion.asJson,
      "deploymentData" -> deploymentData.asJson,
      "modelConfig"    -> modelConfigJson,
    )
    val contentHash = {
      val canonicalJson = contentJson.noSpacesSortKeys
      val hash = MessageDigest
        .getInstance("SHA-1")
        .digest(canonicalJson.getBytes(StandardCharsets.UTF_8))
      Hex.encodeHexString(hash)
    }
    val s3Key   = s"deployment-properties-${contentHash}.json"
    val content = contentJson.spaces2.getBytes(StandardCharsets.UTF_8)
    DeploymentProperties(s3Key, content)
  }

}
