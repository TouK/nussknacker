package pl.touk.nussknacker.engine.avro.schemaregistry

trait BaseSchemaRegistryProvider extends Serializable {

  def schemaRegistryClientFactory: SchemaRegistryClientFactory
}
