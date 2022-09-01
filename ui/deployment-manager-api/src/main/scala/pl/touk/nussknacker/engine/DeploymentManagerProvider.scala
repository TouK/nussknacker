package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
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

  def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig]

  def supportsSignals: Boolean
}

trait TypeSpecificInitialData {
  def forScenario(scenarioName: ProcessName, scenarioType: String): ScenarioSpecificData
  def forFragment(scenarioName: ProcessName, scenarioType: String): FragmentSpecificData = FragmentSpecificData()
}

object TypeSpecificInitialData {
  def apply(forScenario: ScenarioSpecificData,
            forFragment: FragmentSpecificData = FragmentSpecificData()): TypeSpecificInitialData =
    FixedTypeSpecificInitialData(forScenario, forFragment)
}

case class FixedTypeSpecificInitialData(fixedForScenario: ScenarioSpecificData, fixedForFragment: FragmentSpecificData)
  extends TypeSpecificInitialData {

  override def forScenario(scenarioName: ProcessName, scenarioType: String): ScenarioSpecificData = fixedForScenario

  override def forFragment(scenarioName: ProcessName, scenarioType: String): FragmentSpecificData = fixedForFragment

}