package pl.touk.nussknacker.ui.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.util.service.ServiceWithStaticParametersAndReturnType

import scala.concurrent.{ExecutionContext, Future}

class DynamicComponentProvider extends ComponentProvider {

  import net.ceedubs.ficus.Ficus._

  override def providerName: String = "dynamicComponent"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    if (config.getAs[Boolean]("enabled").getOrElse(false))
      List(ComponentDefinition(DynamicProvidedComponent.Name, DynamicProvidedComponent))
    else
      List.empty
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true
}

case object DynamicProvidedComponent extends ServiceWithStaticParametersAndReturnType {
  val Name = "dynamicComponent"
  override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext, collector: InvocationCollectors.ServiceInvocationCollector, contextId: ContextId, metaData: MetaData): Future[Any] = Future.successful(null)
  override def parameters: List[Parameter] = List.empty
  override def returnType: typing.TypingResult = Typed[Void]
}
