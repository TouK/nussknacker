package pl.touk.nussknacker.engine.flink.api

import java.util
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters
import pl.touk.nussknacker.engine.api.ProcessVersion
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flink REST API/webconsole
case class NkGlobalParameters(buildInfo: String,
                              processVersion: ProcessVersion,
                              deploymentData: DeploymentData,
                              configParameters: Option[ConfigGlobalParameters],
                              namingParameters: Option[NamingParameters],
                              private val additionalInformationSerializer: AdditionalInformationSerializer) extends GlobalJobParameters {

  //here we decide which configuration properties should be shown in REST API etc.
  //NOTE: some of the information is used in FlinkRestManager - any changes here should be reflected there
  //AdditionalInformationSerializer is used to expose fields for e.g. easier job analysis, custom implementation can be provided
  override def toMap: util.Map[String, String] = {

    val baseProperties = Map[String, String](
      "buildInfo" -> buildInfo,
      "versionId" -> processVersion.versionId.toString,
      "modelVersion" -> processVersion.modelVersion.map(_.toString).orNull,
      "user" -> processVersion.user
    )
    val baseDeploymentInfo = Map("deployment.user" -> deploymentData.user.id, "deployment.id" -> deploymentData.deploymentId.value)
    val additionalInfo = additionalInformationSerializer.toMap(this)

    val configMap = baseProperties ++ baseDeploymentInfo ++ additionalInfo
    //we wrap in HashMap because .asJava creates not-serializable map in 2.11
    new util.HashMap(configMap.filterNot(_._2 == null).asJava)
  }

}

trait AdditionalInformationSerializer extends Serializable {

  def toMap(nkGlobalParameters: NkGlobalParameters): Map[String, String]

}

object DefaultAdditionalInformationSerializer extends AdditionalInformationSerializer {
  override def toMap(nkGlobalParameters: NkGlobalParameters): Map[String, String] = {
    val deploymentData = nkGlobalParameters.deploymentData
    deploymentData.additionalDeploymentData.map {
      case (k, v) => s"deployment.properties.$k" -> v
    }
  }
}

//this is part of global parameters that is parsed with typesafe Config (e.g. from application.conf/model.conf)
case class ConfigGlobalParameters(explicitUidInStatefulOperators: Option[Boolean],
                                  useTypingResultTypeInformation: Option[Boolean],
                                  //TODO: temporary, until we confirm that IOMonad is not causing problems
                                  useIOMonadInInterpreter: Option[Boolean])

case class NamingParameters(tags: Map[String, String])

object NkGlobalParameters {

  def apply(buildInfo: String, processVersion: ProcessVersion, deploymentData: DeploymentData, modelConfig: Config, namingParameters: Option[NamingParameters] = None): NkGlobalParameters = {
    val configGlobalParameters = modelConfig.getAs[ConfigGlobalParameters]("globalParameters")
    NkGlobalParameters(buildInfo, processVersion, deploymentData, configGlobalParameters, namingParameters, DefaultAdditionalInformationSerializer)
  }

  def setInContext(ec: ExecutionConfig, globalParameters: NkGlobalParameters): Unit = {
    ec.setGlobalJobParameters(globalParameters)
  }

  def readFromContext(ec: ExecutionConfig): Option[NkGlobalParameters] = Option(ec.getGlobalJobParameters).collect {
    case a: NkGlobalParameters => a
  }

}
