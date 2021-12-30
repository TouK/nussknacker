package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax.EncoderOps
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import org.apache.commons.codec.digest.DigestUtils
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{CirceUtil, LiteStreamMetaData, ProcessVersion}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager._
import pl.touk.nussknacker.k8s.manager.K8sUtils.{sanitizeLabel, sanitizeObjectName}
import play.api.libs.json.Format
import skuber.LabelSelector.dsl._
import skuber.LabelSelector.{IsEqualRequirement, Requirement}
import skuber.api.client.LoggingContext
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{ConfigMap, Container, EnvVar, LabelSelector, ListResource, ObjectMeta, ObjectResource, Pod, ResourceDefinition, Volume, k8sInit}
import sttp.client.{NothingT, SttpBackend}

import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/*
  Each scenario is deployed as Deployment+ConfigMap
  ConfigMap contains: model config with overrides for execution and scenario
 */
class K8sDeploymentManagerProvider extends DeploymentManagerProvider {
  override def createDeploymentManager(modelData: ModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                       sttpBackend: SttpBackend[Future, Nothing, NothingT],
                                       deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    K8sDeploymentManager(modelData, config)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(LiteStreamMetaData(Some(1)))

  override def supportsSignals: Boolean = false

  override def name: String = "lite-streaming-k8s"
}

case class K8sDeploymentManagerConfig(dockerImageName: String = "touk/nussknacker-lite-kafka-runtime",
                                      dockerImageTag: String = BuildInfo.version,
                                      configExecutionOverrides: Config = ConfigFactory.empty(),
                                      //TODO: add other settings? This one is mainly for testing lack of progress faster
                                      progressDeadlineSeconds: Option[Int] = None)

class K8sDeploymentManager(modelData: ModelData, config: K8sDeploymentManagerConfig)
                          (implicit ec: ExecutionContext, actorSystem: ActorSystem) extends BaseDeploymentManager with LazyLogging {

  private val k8s = k8sInit
  private val serializedModelConfig = {
    val inputConfig = modelData.inputConfigDuringExecution
    val withOverrides = config.configExecutionOverrides.withFallback(inputConfig.config.withoutPath("classPath"))
    inputConfig.copy(config = wrapInModelConfig(withOverrides)).serialized
  }

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData,
                      processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    for {
      configMap <- createOrUpdate(configMapForData(processVersion, processDeploymentData))
      //we append hash to configMap name so we can guarantee pods will be restarted.
      //They *probably* will restart anyway, as scenario version is in label, but e.g. if only model config is changed?
      deployment <- createOrUpdate(deploymentForData(processVersion, configMap.name))
      //we don't wait until deployment succeeds before deleting old map, but for now we don't rollback anyway
      //https://github.com/kubernetes/kubernetes/issues/22368#issuecomment-790794753
      _ <- k8s.deleteAllSelected[ListResource[ConfigMap]](LabelSelector(
        requirementForName(processVersion.processName),
        configMapIdLabel isNot configMap.name
      ))
    } yield {
      logger.debug(s"Deployed ${processVersion.processName.value}, with deployment: ${deployment.name}, configmap: ${configMap.name}")
      None
    }
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    val selector: LabelSelector = requirementForName(name)
    //We wait for deployment removal before removing configmaps,
    //in case of crash it's better to have unnecessary configmaps than deployments without config
    for {
      deployments <- k8s.deleteAllSelected[ListResource[Deployment]](selector)
      configMaps <- k8s.deleteAllSelected[ListResource[ConfigMap]](selector)
    } yield {
      logger.debug(s"Canceled ${name.value}, removed deployments: ${deployments.itemNames}, configmaps: ${configMaps.itemNames}")
      ()
    }
  }

  override def test[T](name: ProcessName, processJson: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = {
    Future {
      modelData.withThisAsContextClassLoader {
        val espProcess = ScenarioParser.parseUnsafe(processJson)
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    val mapper = new K8sDeploymentStatusMapper(processStateDefinitionManager)
    k8s.listSelected[ListResource[Deployment]](requirementForName(name)).map(_.items).map(mapper.findStatusForDeployments)
  }

  //TODO: use https://kubernetes.io/docs/reference/using-api/server-side-apply/ in the future
  private def createOrUpdate[O<:ObjectResource](data: O)
                                               (implicit fmt: Format[O], rd: ResourceDefinition[O], lc: LoggingContext): Future[O] = {
    k8s.getOption[O](data.name).flatMap {
      case Some(_) => k8s.update(data)
      case None => k8s.create(data)
    }
  }

  protected def configMapForData(processVersion: ProcessVersion, deploymentData: ProcessDeploymentData): ConfigMap = {
    val scenario = deploymentData.asInstanceOf[GraphProcess].processAsJson
    //we simulate (more or less) --append-hash kubectl behaviour...
    val hash = shortHash(scenario + serializedModelConfig)
    val objectName = objectNameForScenario(processVersion, s"-$hash")
    ConfigMap(
      metadata = ObjectMeta(
        name = objectName,
        labels = labelsForScenario(processVersion) + (configMapIdLabel -> objectName)
      ), data = Map(
        "scenario.json" -> scenario,
        "modelConfig.conf" -> serializedModelConfig
      )
    )
  }

  protected def deploymentForData(processVersion: ProcessVersion, configMapId: String): Deployment = {
    val image = s"${config.dockerImageName}:${config.dockerImageTag}"
    val objectName = objectNameForScenario(processVersion)
    val annotations = Map(
      scenarioVersionAnnotation -> processVersion.asJson.spaces2
    )
    val labels = labelsForScenario(processVersion)
    Deployment(
      metadata = ObjectMeta(
        name = objectName,
        labels = labels,
        annotations = annotations
      ),
      spec = Some(Deployment.Spec(
        //TODO: replica count configuration
        replicas = Some(2),
        //TODO: configurable strategy?
        strategy = Some(Deployment.Strategy.Recreate),
        //here we use id to avoid sanitization problems
        selector = LabelSelector(IsEqualRequirement(scenarioIdLabel, processVersion.processId.value.toString)),
        progressDeadlineSeconds = config.progressDeadlineSeconds,
        minReadySeconds = 10,
        template = Pod.Template.Spec(
          metadata = ObjectMeta(
            name = objectName,
            labels = labels
          ), spec = Some(
            Pod.Spec(containers = List(
              Container(
                name = "runtime",
                image = image,
                env = List(
                  EnvVar("SCENARIO_FILE", "/data/scenario.json"),
                  EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf")
                ),
                volumeMounts = List(
                  Volume.Mount(name = "configmap", mountPath = "/data")
                )
              )),
              volumes = List(
                Volume("configmap", Volume.ConfigMapVolumeSource(configMapId))
              )
            ))
        )
      )))
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = K8sProcessStateDefinitionManager

  private def wrapInModelConfig(config: Config): Config = {
    ConfigFactory.parseMap(Collections.singletonMap("modelConfig", config.root()))
  }

}

object K8sDeploymentManager {

  val scenarioNameLabel: String = "nussknacker.io/scenarioName"

  val scenarioIdLabel: String = "nussknacker.io/scenarioId"

  val scenarioVersionLabel: String = "nussknacker.io/scenarioVersion"

  val scenarioVersionAnnotation: String = "nussknacker.io/scenarioVersion"

  val configMapIdLabel: String = "nussknacker.io/configMapId"

  def apply(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem): K8sDeploymentManager = {
    new K8sDeploymentManager(modelData, config.rootAs[K8sDeploymentManagerConfig])
  }

  /*
    We use name label to find deployment to cancel/findStatus, we append short hash to reduce collision risk
    if sanitized names are similar (e.g. they differ only in truncated part or non-alphanumeric characters
    TODO: Maybe it would be better to just pass ProcessId in findJobStatus/cancel?
   */
  private def scenarioNameLabelValue(processName: ProcessName) = {
    val name = processName.value
    sanitizeLabel(name, s"-${shortHash(name)}")
  }

  private[manager] def requirementForName(processName: ProcessName): Requirement = scenarioNameLabel is scenarioNameLabelValue(processName)

  /*
    Labels contain scenario name, scenario id and version.
   */
  private[manager] def labelsForScenario(processVersion: ProcessVersion) = Map(
    scenarioNameLabel -> scenarioNameLabelValue(processVersion.processName),
    scenarioIdLabel -> processVersion.processId.value.toString,
    scenarioVersionLabel -> processVersion.versionId.value.toString
  )

  /*
    Id of both is created with scenario id + sanitized name. This is to:
    - guarantee uniqueness - id is sufficient for that, sanitized name - not necessarily, as replacement/shortening may lead to duplicates
      (other way to mitigate this would be to generate some hash, but it's a bit more complex...)
    - ensure some level of readability - only id would be hard to match name to scenario
   */
  private[manager] def objectNameForScenario(processVersion: ProcessVersion, append: String = ""): String = {
    sanitizeObjectName(s"scenario-${processVersion.processId.value}-${processVersion.processName.value}", append)
  }

  private[manager] def parseVersionAnnotation(deployment: Deployment): Option[ProcessVersion] = {
    deployment.metadata.annotations.get(scenarioVersionAnnotation).flatMap(CirceUtil.decodeJson[ProcessVersion](_).toOption)
  }

  //https://github.com/kubernetes/kubectl/blob/master/pkg/util/hash/hash.go#L105 - we don't care about bad words...
  private[manager] def shortHash(data: String): String = DigestUtils.sha256Hex(data).take(10)

}