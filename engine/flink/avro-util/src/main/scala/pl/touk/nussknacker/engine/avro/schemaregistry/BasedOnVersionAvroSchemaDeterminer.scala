package pl.touk.nussknacker.engine.avro.schemaregistry

import cats.data.Validated
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.avro.{AvroSchemaDeterminer, SchemaDeterminerError}

class BasedOnVersionAvroSchemaDeterminer(createSchemaRegistryClient: () => SchemaRegistryClient,
                                         topic: String,
                                         version: Option[Int]) extends AvroSchemaDeterminer {

  @transient private lazy val schemaRegistryClient: SchemaRegistryClient = createSchemaRegistryClient()

  override def determineSchemaUsedInTyping: Validated[SchemaDeterminerError, Schema] =
    schemaRegistryClient
      .getFreshSchema(topic, version, isKey = false)
      .leftMap(err => new SchemaDeterminerError(s"Fetching schema error for topic: $topic, version: $version", err))

  override def toRuntimeSchema(schemaUsedInTyping: Schema): Option[Schema] = Some(schemaUsedInTyping)

}

/**
 * This implementation uses concrete schema version during typing, but in runtime uses schema from record.
 * It can be useful when you want to have basic typing before deploy but during runtime don't want to do schema evolution -
 * just trying to use record as it is. So this is the case when:
 * - I have #input of type schemaA
 * - I want to to typed filtering on #input.field1
 * - I want to pass #input to sink, with all (possibly unknown at deployment time) fields
 */
class UsingRecordSchemaInRuntimeAvroSchemaDeterminer(createSchemaRegistryClient: () => SchemaRegistryClient,
                                                     topic: String,
                                                     version: Option[Int]) extends BasedOnVersionAvroSchemaDeterminer(createSchemaRegistryClient, topic, version) {

  override def toRuntimeSchema(schemaUsedInTyping: Schema): Option[Schema] = None

}