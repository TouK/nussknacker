package pl.touk.nussknacker.engine.avro

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.CachedConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, SchemaRegistryCacheConfig, SchemaRegistryClientKafkaConfig}

object TestSchemaRegistryClientFactory {
  def apply(schemaRegistryMockClient: CSchemaRegistryClient): CachedConfluentSchemaRegistryClientFactory =
    new CachedConfluentSchemaRegistryClientFactory {
      override def confluentClient(config: SchemaRegistryClientKafkaConfig): CSchemaRegistryClient = {
          schemaRegistryMockClient
      }
    }
}

object SchemaMainTest extends LazyLogging  {
  def main(args: Array[String]): Unit = {
    println("Start")
    val client = CachedConfluentSchemaRegistryClientFactory.create(SchemaRegistryClientKafkaConfig(
      Map(

        "ssl.endpoint.identification.algorithm"-> "",

      ),
      SchemaRegistryCacheConfig()
    ))
    val avroschema = client.client.getLatestSchemaMetadata("testavroschema")
    val jsonschema = client.client.getLatestSchemaMetadata("testjsonschema")
    println(avroschema)
  }
}