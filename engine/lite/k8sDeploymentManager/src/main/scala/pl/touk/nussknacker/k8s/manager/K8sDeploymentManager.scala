package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.lite.kafka.KafkaTransactionalScenarioInterpreter
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, TypeSpecificInitialData}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.scenarioLabel
import skuber.LabelSelector.IsEqualRequirement
import skuber.apps.v1.Deployment
import skuber.json.format._
import skuber.{ConfigMap, Container, EnvVar, LabelSelector, ObjectMeta, Pod, Volume, k8sInit}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

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
                           envVars: Map[String, String])
                          (implicit ec: ExecutionContext, actorSystem: ActorSystem) extends BaseDeploymentManager with LazyLogging {

  private val k8s = k8sInit

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData,
                      processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    logger.debug(s"Deploying using docker image: $dockerImageName:$dockerImageTag")
    val name = nameForVersion(processVersion)
    val scenario = processDeploymentData.asInstanceOf[GraphProcess].processAsJson
    Future.sequence(List(
      k8s.create[ConfigMap](ConfigMap(
        metadata = ObjectMeta(
          name = name,
          labels = Map(scenarioLabel -> processVersion.processName.value)
        ), data = Map("scenario.json" -> scenario)
      )),
      k8s.create[Deployment](
        Deployment(
          metadata = ObjectMeta(
            name = name,
            labels = Map(scenarioLabel -> processVersion.processName.value)
          ),
          spec = Some(Deployment.Spec(
            replicas = Some(1),
            selector = LabelSelector(IsEqualRequirement(scenarioLabel, processVersion.processName.value)),
            template = Pod.Template.Spec(
              metadata = ObjectMeta(
                name = name,
                labels = Map(scenarioLabel -> processVersion.processName.value)
              ), spec = Some(
                Pod.Spec(containers = List(
                  Container(
                    name = "runtime",
                    image = s"$dockerImageName:$dockerImageTag",
                    env = List(
                      EnvVar("SCENARIO_FILE", "/scenario.json"),
                    ) ++ envVars.map { case (k, v) => EnvVar(k, v) },
                    volumeMounts = List(
                      Volume.Mount(name = "scenario", mountPath = "/scenario.json", subPath = "scenario.json")
                    )
                  )),
                  volumes = List(
                    Volume("scenario", Volume.ConfigMapVolumeSource(name))
                  )
                ))
            )
          )))
      ))).map { createResult =>
      logger.info(s"Created deployment: $createResult")
      None
    }
  }

  //TODO: generate correct name for 'strange' scenario names
  private[manager] def nameForVersion(processVersion: ProcessVersion): String = {
    processVersion.processName.value.toLowerCase
  }

  // TODO: implement
  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    ???
  }

  override def test[T](name: ProcessName, processJson: String, testData: TestProcess.TestData, variableEncoder: Any => T): Future[TestProcess.TestResults[T]] = {
    Future {
      modelData.withThisAsContextClassLoader {
        val espProcess = ScenarioParser.parseUnsafe(processJson)
        KafkaTransactionalScenarioInterpreter.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  override def findJobStatus(name: ProcessName): Future[Option[ProcessState]] = Future.successful(None)

}

object K8sDeploymentManager {

  import net.ceedubs.ficus.Ficus._

  val scenarioLabel: String = "nussknacker.io/scenario"

  def apply(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem): K8sDeploymentManager = {
    new K8sDeploymentManager(
      modelData,
      config.getAs[String]("dockerImageName").getOrElse("touk/nussknacker-lite-kafka-runtime"),
      config.getAs[String]("dockerImageTag").getOrElse(BuildInfo.version),
      config.getAs[Map[String, String]]("env").getOrElse(Map.empty)
    )
  }

}