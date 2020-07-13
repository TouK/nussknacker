package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import cats.data.Validated.Valid
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.AvroSchemaDeterminer

class BasedOnVersionAvroSchemaDeterminer(createSchemaRegistryClient: () => SchemaRegistryClient,
                                         topic: String,
                                         version: Option[Int]) extends AvroSchemaDeterminer {

  @transient private lazy val schemaRegistryClient: SchemaRegistryClient = createSchemaRegistryClient()


  override def determineSchemaInRuntime: Validated[SchemaRegistryError, Option[Schema]] =
    determineSchemaUsedInTyping.map(Some(_))

  override def determineSchemaUsedInTyping: Validated[SchemaRegistryError, Schema] =
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)

}

/**
 * This implementation uses concrete schema version during typing, but in runtime uses schema from record.
 * It can be useful when you want to have basic typing before deploy but during runtime don't want to do schema evolution -
 * just trying to use record as it is.
 */
class UsingRecordSchemaInRuntimeAvroSchemaDeterminer(createSchemaRegistryClient: () => SchemaRegistryClient,
                                                     topic: String,
                                                     version: Option[Int]) extends BasedOnVersionAvroSchemaDeterminer(createSchemaRegistryClient, topic, version) {

  override def determineSchemaInRuntime: Validated[SchemaRegistryError, Option[Schema]] = Valid(None)

}