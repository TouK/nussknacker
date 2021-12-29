package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{labelSelectorForName, objectNameForScenario, scenarioIdLabel, scenarioNameLabel, scenarioVersionLabel}
import pl.touk.nussknacker.k8s.manager.K8sUtils.{sanitizeLabel, sanitizeObjectName}
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.LabelSelector.dsl._
import skuber.{ConfigMap, Container, EnvVar, LabelSelector, ListResource, ObjectMeta, Pod, Volume, k8sInit}
import sttp.client.{NothingT, SttpBackend}

import java.util.Collections
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/*
  Each scenario is deployed as Deployment+ConfigMap
  ConfigMap contains: model config with overrides for execution and scenario

  TODO:
   - better implementations of cancel, status, redeployment
   - maybe ConfigMap should have version in its id? Can we guarantee correct one is read when redeploying?
   - more data in annotations? Are they needed?
   - label for name is used to cancel/findStatus. One way to solve it can be using ProcessId in findJobStatus/cancel to avoid uniqueness problems after sanitization
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

class K8sDeploymentManager(modelData: ModelData,
                           dockerImageName: String,
                           dockerImageTag: String,
                           configOverrides: Config)
                          (implicit ec: ExecutionContext, actorSystem: ActorSystem) extends BaseDeploymentManager with LazyLogging {

  private val k8s = k8sInit

  private def wrapInModelConfig(config: Config): Config = {
    ConfigFactory.parseMap(Collections.singletonMap("modelConfig", config.root()))
  }

  private val serializedModelConfig = {
    val inputConfig = modelData.inputConfigDuringExecution
    val withOverrides = configOverrides.withFallback(inputConfig.config.withoutPath("classPath"))
    inputConfig.copy(config = wrapInModelConfig(withOverrides)).serialized
  }

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData,
                      processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Deploying using docker image: $dockerImageName:$dockerImageTag")
    val objectName = objectNameForScenario(processVersion)
    val scenario = processDeploymentData.asInstanceOf[GraphProcess].processAsJson

    val labels = Map(
      scenarioNameLabel -> sanitizeLabel(processVersion.processName.value),
      scenarioIdLabel -> processVersion.processId.value.toString,
      scenarioVersionLabel -> processVersion.versionId.value.toString
    )

    Future.sequence(List(
      k8s.create[ConfigMap](ConfigMap(
        metadata = ObjectMeta(
          name = objectName,
          labels = labels
        ), data = Map(
          "scenario.json" -> scenario,
          "modelConfig.conf" -> serializedModelConfig
        )
      )),
      k8s.create[Deployment](
        Deployment(
          metadata = ObjectMeta(
            name = objectName,
            labels = labels
          ),
          spec = Some(Deployment.Spec(
            //TODO: replica count configuration
            replicas = Some(2),
            //TODO: configurable strategy?
            strategy = Some(Deployment.Strategy.Recreate),
            //here we use id to avoid sanitization problems
            selector = LabelSelector(IsEqualRequirement(scenarioIdLabel, processVersion.processId.value.toString)),
            template = Pod.Template.Spec(
              metadata = ObjectMeta(
                name = objectName,
                labels = labels
              ), spec = Some(
                Pod.Spec(containers = List(
                  Container(
                    name = "runtime",
                    image = s"$dockerImageName:$dockerImageTag",
                    env = List(
                      EnvVar("SCENARIO_FILE", "/data/scenario.json"),
                      EnvVar("CONFIG_FILE", "/opt/nussknacker/conf/application.conf,/data/modelConfig.conf")
                    ),
                    volumeMounts = List(
                      Volume.Mount(name = "configmap", mountPath = "/data")
                    )
                  )),
                  volumes = List(
                    Volume("configmap", Volume.ConfigMapVolumeSource(objectName))
                  )
                ))
            )
          )))
      ))).map { createResult =>
      logger.info(s"Created deployment: $createResult")
      None
    }
  }

  // TODO: implement correctly
  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    val selector = labelSelectorForName(name)
    Future.sequence(List(
      k8s.deleteAllSelected[ListResource[Deployment]](selector),
      k8s.deleteAllSelected[ListResource[ConfigMap]](selector),
    )).map(_ => ())
  }

  override def test[T](name: ProcessName, processJson: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = {
    Future {
      modelData.withThisAsContextClassLoader {
        val espProcess = ScenarioParser.parseUnsafe(processJson)
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  //TODO: real implementation
  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = {
    k8s.listSelected[ListResource[Deployment]](labelSelectorForName(name)).map(_.items).map {
      case Nil => None
      case one :: Nil if one.status.exists(_.readyReplicas > 0) =>
        Some(ProcessState("", SimpleStateStatus.Running, None, processStateDefinitionManager))
      case _ =>
        Some(ProcessState("", SimpleStateStatus.Failed, None, processStateDefinitionManager))
    }
  }

}

object K8sDeploymentManager {

  import net.ceedubs.ficus.Ficus._

  val scenarioNameLabel: String = "nussknacker.io/scenarioName"

  val scenarioIdLabel: String = "nussknacker.io/scenarioId"

  val scenarioVersionLabel: String = "nussknacker.io/scenarioVersion"

  /*
    Labels contain scenario name, scenario id and version.
    We use name label to find deployment to cancel/findStatus, it *won't* work properly if sanitized names
    are not unique
    TODO: we need some hash to avoid name clashes :/ Or pass ProcessId in findJobStatus/cancel
   */
  private[manager] def labelSelectorForName(processName: ProcessName) =
    LabelSelector(scenarioNameLabel is sanitizeLabel(processName.value))

  /*
    Id of both is created with scenario id + sanitized name. This is to:
    - guarantee uniqueness - id is sufficient for that, sanitized name - not necessarily, as replacement/shortening may lead to duplicates
      (other way to mitigate this would be to generate some hash, but it's a bit more complex...)
    - ensure some level of readability - only id would be hard to match name to scenario
   */
  private[manager] def objectNameForScenario(processVersion: ProcessVersion): String = {
    sanitizeObjectName(s"scenario-${processVersion.processId.value}-${processVersion.processName}")
  }

  def apply(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem): K8sDeploymentManager = {
    new K8sDeploymentManager(
      modelData,
      config.getAs[String]("dockerImageName").getOrElse("touk/nussknacker-lite-kafka-runtime"),
      config.getAs[String]("dockerImageTag").getOrElse(BuildInfo.version),
      config.getAs[Config]("configExecutionOverrides").getOrElse(ConfigFactory.empty())
    )
  }

}