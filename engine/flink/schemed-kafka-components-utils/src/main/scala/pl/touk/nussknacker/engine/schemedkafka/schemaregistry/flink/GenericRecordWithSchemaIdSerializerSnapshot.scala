package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema
import org.apache.flink.annotation.VisibleForTesting
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSchemaCompatibility, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.types.StringValue.{readString, writeString}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  GenericRecordWithSchemaId,
  IntSchemaId,
  SchemaId,
  StringSchemaId
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink.GenericRecordWithSchemaIdSerializerSnapshot.{
  INT_MARKER,
  STRING_MARKER
}

import java.util

class GenericRecordWithSchemaIdSerializerSnapshot
    extends TypeSerializerSnapshot[GenericRecordWithSchemaId]
    with LazyLogging {

  private type SchemasMap = util.Map[Int, util.Map[SchemaId, Schema]]

  private var schemas: SchemasMap = _

  def this(schemas: util.Map[Int, util.Map[SchemaId, Schema]]) = {
    this()
    this.schemas = GenericRecordWithSchemaIdSerializer.cloneSchemas(schemas)
  }

  override def getCurrentVersion: Int = 1

  override def writeSnapshot(out: DataOutputView): Unit = {
    out.writeInt(schemas.size())

    schemas.forEach({ (schemaRegistryId, srSchemas) =>
      out.writeInt(schemaRegistryId)
      out.writeInt(srSchemas.size())
      srSchemas.forEach({ (schemaId, schema) =>
        schemaId match {
          case IntSchemaId(value) =>
            out.writeByte(INT_MARKER)
            out.writeInt(value)
          case StringSchemaId(value) =>
            out.writeByte(STRING_MARKER)
            out.writeUTF(value)
        }
        writeString(schema.toString, out)
      })
    })
  }

  override def readSnapshot(readVersion: Int, in: DataInputView, userCodeClassLoader: ClassLoader): Unit = {
    val schemaParser = new Schema.Parser()

    val srCount = in.readInt()
    schemas = new util.HashMap(srCount)
    for (_ <- 1 to srCount) {
      val schemaRegistryId = in.readInt()
      val schemaCount      = in.readInt()
      val srSchemas        = new util.HashMap[SchemaId, Schema](schemaCount)
      for (_ <- 1 to schemaCount) {
        val schemaId = in.readByte() match {
          case INT_MARKER    => IntSchemaId(in.readInt())
          case STRING_MARKER => StringSchemaId(in.readUTF())
          case other =>
            throw new IllegalStateException(s"Cound not read serializer snapshot, unknown schema type marker: $other")
        }
        val schema = schemaParser.parse(readString(in))
        srSchemas.put(schemaId, schema)
      }
      schemas.put(schemaRegistryId, srSchemas)
    }
  }

  override def restoreSerializer(): TypeSerializer[GenericRecordWithSchemaId] =
    new GenericRecordWithSchemaIdSerializer(GenericRecordWithSchemaIdSerializer.cloneSchemas(schemas))

  override def resolveSchemaCompatibility(
      oldSerializerSnapshot: TypeSerializerSnapshot[GenericRecordWithSchemaId]
  ): TypeSerializerSchemaCompatibility[GenericRecordWithSchemaId] = {
    oldSerializerSnapshot match {
      case oldSnapshot: GenericRecordWithSchemaIdSerializerSnapshot =>
        // The serializer created by TypeInformation.createSerializer() starts with null schemas
        // and cannot deserialize saved state. We always provide a reconfigured serializer
        // with merged schemas so Flink uses it to read existing state instead
        TypeSerializerSchemaCompatibility.compatibleWithReconfiguredSerializer(
          new GenericRecordWithSchemaIdSerializer(mergeSchemasMaps(oldSnapshot.schemas, schemas))
        )
      case _ =>
        TypeSerializerSchemaCompatibility.incompatible()
    }
  }

  // Produces a new map with all entries from both old and new; new schemas take precedence on overlap
  // (conflicts are already ruled out by schemasConflict, so both schemas are equal in that case).
  private def mergeSchemasMaps(oldSchemas: SchemasMap, newSchemas: SchemasMap): SchemasMap = {
    if (oldSchemas == null) {
      newSchemas
    } else if (newSchemas == null) {
      oldSchemas
    } else {
      val merged = new util.HashMap[Int, util.Map[SchemaId, Schema]](oldSchemas.size() + newSchemas.size())

      oldSchemas.forEach((schemaRegistryId, srSchemas) => merged.put(schemaRegistryId, new util.HashMap(srSchemas)))
      newSchemas.forEach { (schemaRegistryId, srSchemas) =>
        val mergedSrSchemas = merged.computeIfAbsent(schemaRegistryId, _ => new util.HashMap())
        srSchemas.forEach((schemaId, schema) => {
          mergedSrSchemas.merge(
            schemaId,
            schema,
            (prevSchema, newSchema) => {
              if (prevSchema != newSchema) {
                logger.warn(
                  s"Encountered differing schemas for schemaRegistryId {} and schemaId {}\nOld: {}\nNew: {}",
                  schemaRegistryId,
                  schema,
                  prevSchema,
                  newSchema
                )
              }
              newSchema
            }
          )
        })
      }

      merged
    }
  }

  @VisibleForTesting
  private[flink] def getSchemas: SchemasMap = schemas

}

object GenericRecordWithSchemaIdSerializerSnapshot {
  private val INT_MARKER: Byte    = 1
  private val STRING_MARKER: Byte = 2
}
