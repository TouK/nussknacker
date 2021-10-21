package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.generic.sinks.GenericKafkaJsonSink
import pl.touk.nussknacker.engine.kafka.generic.sources.{GenericJsonSourceFactory, GenericTypedJsonSourceFactory}

class KafkaComponentsProvider extends ComponentProvider {

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = List(
    ComponentDefinition("kafka-json", new GenericKafkaJsonSink(dependencies)),
    ComponentDefinition("kafka-json", new GenericJsonSourceFactory(dependencies)),
    ComponentDefinition("kafka-typed-json", new GenericTypedJsonSourceFactory(dependencies)),
  )

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
