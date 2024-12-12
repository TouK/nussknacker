package pl.touk.nussknacker.engine.flink.api

import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters.NkGlobalParametersToMapEncoder

import _root_.java.util
import scala.jdk.CollectionConverters._

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flink REST API/webconsole
case class NkGlobalParameters(
    buildInfo: String,
    deploymentId: String, // TODO: Pass here DeploymentId?
    processVersion: ProcessVersion,
    configParameters: Option[ConfigGlobalParameters],
    namespaceParameters: Option[NamespaceMetricsTags],
    additionalInformation: Map[String, String]
) extends GlobalJobParameters {

  // here we decide which configuration properties should be shown in REST API etc.
  // NOTE: some of the information is used in FlinkRestManager - any changes here should be reflected there
  override def toMap: util.Map[String, String] =
    // we wrap in HashMap because .asJava creates not-serializable map in 2.11
    new util.HashMap(NkGlobalParametersToMapEncoder.encode(this).filterNot(_._2 == null).asJava)

}

//this is part of global parameters that is parsed with typesafe Config (e.g. from application.conf/model.conf)
case class ConfigGlobalParameters(
    explicitUidInStatefulOperators: Option[Boolean],
    // TODO: temporary, until we confirm that IOMonad is not causing problems
    useIOMonadInInterpreter: Option[Boolean],
    forceSyncInterpretationForSyncScenarioPart: Option[Boolean],
)

case class NamespaceMetricsTags(tags: Map[String, String])

object NamespaceMetricsTags {

  private val originalNameTag = "originalProcessName"
  private val namespaceTag    = "namespace"

  def apply(scenarioName: String, namingStrategy: NamingStrategy): Option[NamespaceMetricsTags] = {
    namingStrategy.namespace.map { namespace =>
      NamespaceMetricsTags(
        Map(
          originalNameTag -> scenarioName,
          namespaceTag    -> namespace
        )
      )
    }
  }

}

object NkGlobalParameters {

  def create(
      buildInfo: String,
      deploymentId: String, // TODO: Pass here DeploymentId?
      processVersion: ProcessVersion,
      modelConfig: Config,
      namespaceTags: Option[NamespaceMetricsTags],
      additionalInformation: Map[String, String]
  ): NkGlobalParameters = {
    val configGlobalParameters = modelConfig.getAs[ConfigGlobalParameters]("globalParameters")
    NkGlobalParameters(
      buildInfo,
      deploymentId,
      processVersion,
      configGlobalParameters,
      namespaceTags,
      additionalInformation
    )
  }

  def setInContext(ec: ExecutionConfig, globalParameters: NkGlobalParameters): Unit = {
    ec.setGlobalJobParameters(globalParameters)
  }

  def readFromContext(ec: ExecutionConfig): Option[NkGlobalParameters] =
    NkGlobalParametersToMapEncoder.decode(ec.getGlobalJobParameters.toMap.asScala.toMap)

  private object NkGlobalParametersToMapEncoder {

    def encode(parameters: NkGlobalParameters): Map[String, String] = {
      def encodeWithKeyPrefix(map: Map[String, String], prefix: String): Map[String, String] = {
        map.map { case (key, value) => s"$prefix.$key" -> value }
      }

      val baseProperties = Map[String, String](
        "buildInfo"    -> parameters.buildInfo,
        "deploymentId" -> parameters.deploymentId,
        "versionId"    -> parameters.processVersion.versionId.value.toString,
        "processId"    -> parameters.processVersion.processId.value.toString,
        "modelVersion" -> parameters.processVersion.modelVersion.map(_.toString).orNull,
        "labels"       -> Encoder.encodeList[String].apply(parameters.processVersion.labels).noSpaces,
        "user"         -> parameters.processVersion.user,
        "processName"  -> parameters.processVersion.processName.value
      )

      val configMap = parameters.configParameters
        .map(ConfigGlobalParametersToMapEncoder.encode)
        .getOrElse(Map.empty)

      val namespaceTagsMap = parameters.namespaceParameters
        .map(p => encodeWithKeyPrefix(p.tags, namespaceTagsMapPrefix))
        .getOrElse(Map.empty)

      val additionalInformationMap =
        encodeWithKeyPrefix(parameters.additionalInformation, additionalInformationMapPrefix)

      baseProperties ++ additionalInformationMap ++ configMap ++ namespaceTagsMap
    }

    def decode(map: Map[String, String]): Option[NkGlobalParameters] = {
      def decodeWithKeyPrefix(map: Map[String, String], prefix: String): Map[String, String] = {
        map.view
          .filter { case (key, _) => key.startsWith(s"$prefix.") }
          .map { case (key, value) => key.stripPrefix(s"$prefix.") -> value }
          .toMap
      }

      val processVersionOpt = for {
        versionId   <- map.get("versionId").map(v => VersionId(v.toLong))
        processId   <- map.get("processId").map(pid => ProcessId(pid.toLong))
        processName <- map.get("processName").map(ProcessName(_))
        labels      <- map.get("labels").flatMap(value => io.circe.parser.decode[List[String]](value).toOption)
        user        <- map.get("user")
      } yield {
        val modelVersion = map.get("modelVersion").map(_.toInt)
        ProcessVersion(versionId, processName, processId, labels, user, modelVersion)
      }
      val buildInfoOpt = map.get("buildInfo")

      val configParameters = ConfigGlobalParametersToMapEncoder.decode(map)
      val namespaceTags = {
        val namespaceTagsMap = decodeWithKeyPrefix(map, namespaceTagsMapPrefix)
        if (namespaceTagsMap.isEmpty) None else Some(NamespaceMetricsTags(namespaceTagsMap))
      }
      val additionalInformation = decodeWithKeyPrefix(map, additionalInformationMapPrefix)

      for {
        processVersion <- processVersionOpt
        buildInfo      <- buildInfoOpt
        deploymentId   <- map.get("deploymentId")
      } yield NkGlobalParameters(
        buildInfo,
        deploymentId,
        processVersion,
        configParameters,
        namespaceTags,
        additionalInformation
      )
    }

    private object ConfigGlobalParametersToMapEncoder {

      def encode(params: ConfigGlobalParameters): Map[String, String] = {
        Map(
          s"$prefix.explicitUidInStatefulOperators" -> params.explicitUidInStatefulOperators
            .map(_.toString)
            .orNull,
          s"$prefix.useIOMonadInInterpreter" -> params.useIOMonadInInterpreter
            .map(_.toString)
            .orNull,
          s"$prefix.forceSyncInterpretationForSyncScenarioPart" -> params.forceSyncInterpretationForSyncScenarioPart
            .map(_.toString)
            .orNull
        )
      }

      def decode(map: Map[String, String]): Option[ConfigGlobalParameters] = {
        val mapContainsConfigGlobalParams = map.view.exists { case (key, _) => key.startsWith(prefix) }
        if (mapContainsConfigGlobalParams) {
          Some(
            ConfigGlobalParameters(
              explicitUidInStatefulOperators = map.get(s"$prefix.explicitUidInStatefulOperators").map(_.toBoolean),
              useIOMonadInInterpreter = map.get(s"$prefix.useIOMonadInInterpreter").map(_.toBoolean),
              forceSyncInterpretationForSyncScenarioPart =
                map.get(s"$prefix.forceSyncInterpretationForSyncScenarioPart").map(_.toBoolean)
            )
          )
        } else {
          None
        }
      }

      private val prefix = "configParameters"
    }

    private val namespaceTagsMapPrefix         = "namespaceTags"
    private val additionalInformationMapPrefix = "additionalInformation"
  }

}
