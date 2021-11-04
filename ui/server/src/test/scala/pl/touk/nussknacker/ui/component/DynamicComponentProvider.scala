package pl.touk.nussknacker.ui.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}

import scala.concurrent.Future

case object DynamicComponentProvider extends ComponentProvider {
  val SharedComponentName = "sharedProvidedComponent"
  val SingleComponentName = "singleProvidedComponent"
  val KafkaAvroComponentName = "kafkaAvroSameName"

  override def providerName: String = "dynamicComponent"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
      List(
        ComponentDefinition(SharedComponentName, DynamicProvidedService),
        ComponentDefinition(SingleComponentName, DynamicProvidedService),
        ComponentDefinition(KafkaAvroComponentName, SinkFactory.noParam(new Sink {})),
        ComponentDefinition(KafkaAvroComponentName, SourceFactory.noParam(new Source[Map[String, String]] {})),
      )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  case object DynamicProvidedService extends Service {
    @MethodToInvoke def invoke(): Future[Unit] = Future.unit
  }
}
