package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NamedServiceProvider, ScenarioSpecificData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

// If you are adding a new DeploymentManagerProvider available in the public distribution, please remember
// to add it's type to UsageStatisticsHtmlSnippet.knownDeploymentManagerTypes
trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(modelData: BaseModelData, config: Config)
                             (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                              sttpBackend: SttpBackend[Future, Nothing, NothingT],
                              deploymentService: ProcessingTypeDeploymentService): DeploymentManager

  def typeSpecificInitialData(config: Config): TypeSpecificInitialData

  def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = Map.empty

  def additionalValidators(config: Config): List[CustomProcessValidator] = Nil

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
