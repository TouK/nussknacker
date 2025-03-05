package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MethodToInvoke, NodeId, ParamName, Service}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

import scala.concurrent.Future

class TestComponentProvider extends ComponentProvider {
  override def providerName: String                               = "test"
  override def resolveConfigForExecution(config: Config): Config  = config
  override def isAutoLoaded: Boolean                              = true
  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
    ComponentDefinition("env", new EnvService)
  )

}

class EnvService extends Service {

  @MethodToInvoke
  def invoke(@ParamName("name") name: String)(implicit nodeId: NodeId): Future[String] =
    Future.successful(System.getenv(name))

}
