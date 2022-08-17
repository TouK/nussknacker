package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NamedServiceProvider, ScenarioSpecificData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(modelData: BaseModelData, config: Config)
                             (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                              sttpBackend: SttpBackend[Future, Nothing, NothingT],
                              deploymentService: ProcessingTypeDeploymentService): DeploymentManager

  def createQueryableClient(config: Config): Option[QueryableClient]

  def typeSpecificInitialData(config: Config): TypeSpecificInitialData

  def supportsSignals: Boolean
}

case class TypeSpecificInitialData(forScenario: ScenarioSpecificData,
                                   forFragment: FragmentSpecificData = FragmentSpecificData())
