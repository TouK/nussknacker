package pl.touk.nussknacker.engine.lite.components

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, toFicusConfig}
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.sink.UniversalKafkaSinkFactory
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.kafka.consumerrecord.ConsumerRecordDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.BaseGenericTypedJsonSourceFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.{deserializeToMap, deserializeToTypedMap, jsonFormatterFactory}
import pl.touk.nussknacker.engine.kafka.sink.{GenericJsonSerialization, KafkaSinkFactory}
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.util.config.DocsConfig

import java.util
import scala.language.higherKinds

object LiteKafkaComponentProvider {
  val KafkaUniversalName = "kafka"
  val KafkaJsonName = "kafka-json"
  val KafkaTypedJsonName = "kafka-typed-json"
}

class LiteKafkaComponentProvider(schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory) extends ComponentProvider {

  import LiteKafkaComponentProvider._

  def this() = this(CachedConfluentSchemaRegistryClientFactory)

  override def providerName: String = "kafka"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val docsConfig: DocsConfig = new DocsConfig(config)
    import docsConfig._

    val universal = "DataSourcesAndSinks#kafka-source"
    val noTypeInfo = "DataSourcesAndSinks#no-type-information--json-serialization"
    val universalSerdeProvider = ConfluentSchemaBasedSerdeProvider.universal(schemaRegistryClientFactory)

    lazy val lowLevelKafkaComponents = List(
      ComponentDefinition(KafkaJsonName, new KafkaSinkFactory(GenericJsonSerialization(_), dependencies, LiteKafkaSinkImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition(KafkaJsonName, new KafkaSourceFactory[String, util.Map[_, _]](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToMap), jsonFormatterFactory, dependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(noTypeInfo),
      ComponentDefinition(KafkaTypedJsonName, new KafkaSourceFactory[String, TypedMap](
        ConsumerRecordDeserializationSchemaFactory.fixedValueDeserialization(deserializeToTypedMap),
        jsonFormatterFactory, dependencies, new LiteKafkaSourceImplFactory
      ) with BaseGenericTypedJsonSourceFactory).withRelativeDocs("DataSourcesAndSinks#manually-typed--json-serialization"),
    )

    // TODO: change link to the documentation when json schema handling will be available
    val universalKafkaComponents = List(
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSourceFactory(schemaRegistryClientFactory, universalSerdeProvider, dependencies, new LiteKafkaSourceImplFactory)).withRelativeDocs(universal),
      ComponentDefinition(KafkaUniversalName, new UniversalKafkaSinkFactory(schemaRegistryClientFactory, universalSerdeProvider, dependencies, LiteKafkaUniversalSinkImplFactory)).withRelativeDocs(universal)
    )

    //TODO: for now we add this feature flag inside kafka, when this provider can handle multiple kafka brokers move to provider config
    val lowLevelComponentsEnabled = dependencies.config.getAs[Boolean]("kafka.lowLevelComponentsEnabled").getOrElse(KafkaConfig.lowLevelComponentsEnabled)
    if (lowLevelComponentsEnabled) {
      lowLevelKafkaComponents ::: universalKafkaComponents
    } else {
      universalKafkaComponents
    }
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true
}
