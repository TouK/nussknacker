package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentProvider,
  ComponentType,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  UniversalSchemaBasedSerdeProvider,
  UniversalSchemaRegistryClientFactory
}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

object LiteKafkaComponentProvider {
  val KafkaUniversalName = "kafka"
}

class LiteKafkaComponentProvider(schemaRegistryClientFactory: SchemaRegistryClientFactory) extends ComponentProvider {

  import LiteKafkaComponentProvider._

  def this() = this(UniversalSchemaRegistryClientFactory)

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig = DocsConfig(config)
    import docsConfig._
    def universal(componentType: ComponentType) = s"DataSourcesAndSinks#kafka-$componentType"

    val universalSerdeProvider = UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory)

    List(
      ComponentDefinition(
        KafkaUniversalName,
        new UniversalKafkaSourceFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          dependencies,
          new LiteKafkaSourceImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Source)),
      ComponentDefinition(
        KafkaUniversalName,
        new UniversalKafkaSinkFactory(
          schemaRegistryClientFactory,
          universalSerdeProvider,
          dependencies,
          LiteKafkaUniversalSinkImplFactory
        )
      ).withRelativeDocs(universal(ComponentType.Sink))
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
