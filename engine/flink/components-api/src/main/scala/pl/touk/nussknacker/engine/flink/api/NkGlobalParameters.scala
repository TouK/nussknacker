package pl.touk.nussknacker.engine.flink.api

import _root_.java.util
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.api.common.ExecutionConfig.GlobalJobParameters
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.modelinfo.ModelInfo
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.flink.api.NkGlobalParameters.NkGlobalParametersToMapEncoder

import scala.jdk.CollectionConverters._

//we can use this class to pass config through RuntimeContext to places where it would be difficult to use otherwise
//Also, those configuration properties will be exposed via Flink REST API/webconsole
case class NkGlobalParameters(
    // This field is not used anywhere. We should consider if we still need it for example for some diagnosis purpose,
    // or we should remove it from here
    modelInfo: ModelInfo,
    deploymentId: String, // TODO: Pass here DeploymentId?
    // Currently only versionId is used in DeploymentStatusDetails, other fields are redundant. We should consider
    // if we still need them for example for some diagnosis purpose, or we should remove them from here
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
          namespaceTag    -> namespace.value
        )
      )
    }
  }

}

object NkGlobalParameters extends LazyLogging {

  def create(
      modelInfo: ModelInfo,
      deploymentId: String, // TODO: Pass here DeploymentId?
      processVersion: ProcessVersion,
      modelConfig: Config,
      namespaceTags: Option[NamespaceMetricsTags],
      additionalInformation: Map[String, String]
  ): NkGlobalParameters = {
    val configGlobalParameters = modelConfig.getAs[ConfigGlobalParameters]("globalParameters")
    NkGlobalParameters(
      modelInfo,
      deploymentId,
      processVersion,
      configGlobalParameters,
      namespaceTags,
      additionalInformation
    )
  }

  def fromMap(jobParameters: java.util.Map[String, String]): Option[NkGlobalParameters] =
    NkGlobalParametersToMapEncoder.decode(jobParameters.asScala.toMap)

  private object NkGlobalParametersToMapEncoder {

    def encode(parameters: NkGlobalParameters): Map[String, String] = {
      def encodeWithKeyPrefix(map: Map[String, String], prefix: String): Map[String, String] = {
        map.map { case (key, value) => s"$prefix.$key" -> value }
      }

      val baseProperties = Map[String, String](
        // TODO: rename to modelInfo
        "buildInfo"    -> parameters.modelInfo.asJsonString,
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
      val modelInfoOpt = map
        .get("buildInfo")
        .map(ModelInfo.parseJsonString)
        .map(
          _.fold(
            { err =>
              logger.warn(s"Saved model info is not a json's object: ${err.getMessage}. Empty map will be returned")
              ModelInfo.empty
            },
            identity
          )
        )

      val configParameters = ConfigGlobalParametersToMapEncoder.decode(map)
      val namespaceTags = {
        val namespaceTagsMap = decodeWithKeyPrefix(map, namespaceTagsMapPrefix)
        if (namespaceTagsMap.isEmpty) None else Some(NamespaceMetricsTags(namespaceTagsMap))
      }
      val additionalInformation = decodeWithKeyPrefix(map, additionalInformationMapPrefix)

      for {
        processVersion <- processVersionOpt
        modelInfo      <- modelInfoOpt
        deploymentId   <- map.get("deploymentId")
      } yield NkGlobalParameters(
        modelInfo,
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
