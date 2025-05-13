package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelConfig
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  NussknackerVersion,
  StaticParameterConfig
}
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext

import scala.concurrent.{ExecutionContext, Future}

class TestComponentProvider extends ComponentProvider {
  override def providerName: String                               = "test"
  override def resolveConfigForExecution(config: Config): Config  = config
  override def isAutoLoaded: Boolean                              = true
  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def create(componentProviderConfig: Config, modelConfig: ModelConfig): List[ComponentDefinition] = List(
    ComponentDefinition("env", new EnvService),
    ComponentDefinition("deploy-param-service", new DeployParamService),
  )

}

class EnvService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("name") name: String)(implicit ec: ExecutionContext): Future[String] =
    Future {
      System.getenv(name)
    }

}

class DeployParamService extends Service with WithActionParametersSupport {

  @MethodToInvoke
  def invoke(implicit useContext: ComponentUseContext, ec: ExecutionContext): Future[String] =
    Future {
      val deploymentData = useContext.deploymentData.getOrElse(Map.empty)
      deploymentData.get("deployParam").orNull
    }

  override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, StaticParameterConfig]] = Map(
    ScenarioActionName.Deploy -> Map(ParameterName("deployParam") -> StaticParameterConfig.empty)
  )

}
