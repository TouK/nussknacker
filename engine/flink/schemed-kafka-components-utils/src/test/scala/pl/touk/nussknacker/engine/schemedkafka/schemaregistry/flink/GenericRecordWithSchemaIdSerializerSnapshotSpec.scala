package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.avro.{Schema, SchemaBuilder}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaId

import scala.jdk.CollectionConverters._

class GenericRecordWithSchemaIdSerializerSnapshotSpec extends AnyFunSuite with Matchers {

  private lazy val schema1 = SchemaBuilder.record("schema1").fields().requiredString("f1").endRecord()
  private lazy val schema2 = SchemaBuilder.record("schema2").fields().requiredInt("f2").endRecord()
  private lazy val schema3 = SchemaBuilder.record("schema3").fields().requiredBoolean("f3").endRecord()

  test("is compatible after migration") {
    val emptySnapshot = createSnapshot(Map.empty)
    val snapshot1     = createSnapshot(Map(1 -> Map(SchemaId.fromInt(1) -> schema1)))
    val snapshot2     = createSnapshot(Map(1 -> Map(SchemaId.fromInt(1) -> schema1)))

    emptySnapshot.resolveSchemaCompatibility(snapshot1).isCompatibleWithReconfiguredSerializer shouldBe true
    snapshot1.resolveSchemaCompatibility(emptySnapshot).isCompatibleWithReconfiguredSerializer shouldBe true

    snapshot1.resolveSchemaCompatibility(snapshot2).isCompatibleWithReconfiguredSerializer shouldBe true
    snapshot2.resolveSchemaCompatibility(snapshot1).isCompatibleWithReconfiguredSerializer shouldBe true
  }

  test("merges schemas") {
    val emptySnapshot = createSnapshot(Map.empty)
    val snapshot1     = createSnapshot(Map(1 -> Map(SchemaId.fromInt(1) -> schema1)))
    val snapshot2 = createSnapshot(
      Map(
        1 -> Map(SchemaId.fromInt(2) -> schema2),
        4 -> Map(SchemaId.fromString("xyz") -> schema3)
      )
    )

    def getMergedSchemas(
        old: GenericRecordWithSchemaIdSerializerSnapshot,
        `new`: GenericRecordWithSchemaIdSerializerSnapshot
    ) =
      `new`
        .resolveSchemaCompatibility(old)
        .getReconfiguredSerializer
        .asInstanceOf[GenericRecordWithSchemaIdSerializer]
        .getSchemas

    getMergedSchemas(emptySnapshot, snapshot1) shouldBe snapshot1.getSchemas
    getMergedSchemas(snapshot1, emptySnapshot) shouldBe snapshot1.getSchemas
    getMergedSchemas(snapshot1, snapshot2) shouldBe javaSchemas(
      Map(
        1 -> Map(SchemaId.fromInt(1) -> schema1, SchemaId.fromInt(2) -> schema2),
        4 -> Map(SchemaId.fromString("xyz") -> schema3)
      )
    )
  }

  private def javaSchemas(schemas: Map[Int, Map[SchemaId, Schema]]) = schemas.view.mapValues(_.asJava).toMap.asJava

  private def createSnapshot(schemas: Map[Int, Map[SchemaId, Schema]]) =
    new GenericRecordWithSchemaIdSerializerSnapshot(javaSchemas(schemas))
}
