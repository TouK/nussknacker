package pl.touk.nussknacker.engine.management.sample.component

import com.typesafe.config.{Config, ConfigValueFactory}
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

//Sample showing how to achieve dynamic component count based on configuration, evaluated on NK side (e.g. discover of services from external registry)
class SampleComponentProvider extends ComponentProvider {

  override def providerName: String = "dynamicTest"

  override def resolveConfigForExecution(config: Config): Config = {
    val number = config.getAs[Int]("valueCount").getOrElse(0)
    config.withValue("values", ConfigValueFactory.fromIterable((1 to number).map(i => s"v$i").asJava))
  }

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    config.getAs[List[String]]("values").getOrElse(Nil).map { value: String =>
      ComponentDefinition(s"component-$value", SampleProvidedComponent(value))
    }
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true
}

case class SampleProvidedComponent(param: String) extends EagerServiceWithStaticParametersAndReturnType {

  override def invoke(eagerParameters: Map[ParameterName, Any])(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      contextId: ContextId,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[Any] = {
    Future.successful(null)
  }

  override def parameters: List[Parameter] = List(Parameter[String](ParameterName(s"fromConfig-$param")))

  override def returnType: typing.TypingResult = Typed[Void]

}
