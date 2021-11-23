package pl.touk.nussknacker.ui.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}

import scala.concurrent.Future

object DynamicComponentProvider {
  val SharedProvidedComponentName = "sharedProvidedComponent"
  val SingleProvidedComponentName = "singleProvidedComponent"
  val KafkaAvroProvidedComponentName = "kafkaAvroSameName"
  val ProviderName = "dynamicComponent"
}

class DynamicComponentProvider extends ComponentProvider {
  import DynamicComponentProvider._

  override def providerName: String = ProviderName

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
      List(
        ComponentDefinition(SharedProvidedComponentName, DynamicProvidedService),
        ComponentDefinition(SingleProvidedComponentName, DynamicProvidedService),
        ComponentDefinition(KafkaAvroProvidedComponentName, SinkFactory.noParam(new Sink {})),
        ComponentDefinition(KafkaAvroProvidedComponentName, SourceFactory.noParam(new Source {})),
      )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  case object DynamicProvidedService extends Service {
    @MethodToInvoke def invoke(): Future[Unit] = Future.unit
  }
}
