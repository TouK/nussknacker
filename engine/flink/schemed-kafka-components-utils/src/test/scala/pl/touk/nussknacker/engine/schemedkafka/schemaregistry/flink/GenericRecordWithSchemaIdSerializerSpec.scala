package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.avro.SchemaBuilder
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.core.memory.{DataInputDeserializer, DataOutputSerializer}
import org.apache.flink.util.InstantiationUtil
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaId}

class GenericRecordWithSchemaIdSerializerSpec extends AnyFunSuite with Matchers {

  private lazy val testRecord1: GenericRecordWithSchemaId = {
    val schema = SchemaBuilder.record("schema1").fields().requiredString("f1").endRecord()
    val record = new GenericRecordWithSchemaId(schema, 6, SchemaId.fromInt(11))
    record.put("f1", "str1")
    record
  }

  private lazy val testRecord2: GenericRecordWithSchemaId = {
    val schema = SchemaBuilder.record("schema2").fields().requiredInt("f2").endRecord()
    val record = new GenericRecordWithSchemaId(schema, 6, SchemaId.fromInt(22))
    record.put("f2", 5)
    record
  }

  private lazy val testRecord3: GenericRecordWithSchemaId = {
    val schema = SchemaBuilder.record("schema3").fields().requiredBoolean("f3").endRecord()
    val record = new GenericRecordWithSchemaId(schema, 9, SchemaId.fromString("schema-for-record3"))
    record.put("f3", true)
    record
  }

  test("serialize/deserialize") {
    testRoundTrips(List(testRecord1, testRecord2, testRecord3))
  }

  test("can be duplicated") {
    testRoundTrips(List(testRecord1, testRecord2, testRecord3), _.duplicate())
  }

  test("can be serialized and deserialized") {
    testRoundTrips(
      List(testRecord1, testRecord2, testRecord3),
      { s =>
        val serializedSerializer = InstantiationUtil.serializeObject(s)
        InstantiationUtil.deserializeObject[GenericRecordWithSchemaIdSerializer](
          serializedSerializer,
          Thread.currentThread().getContextClassLoader
        )
      }
    )
  }

  test("can be recreated from serializer snapshot") {
    testRoundTrips(
      List(testRecord1, testRecord2, testRecord3),
      { s =>
        val snapshotData = new DataOutputSerializer(100)
        s.snapshotConfiguration().writeSnapshot(snapshotData)

        val snapshot = new GenericRecordWithSchemaIdSerializerSnapshot()
        snapshot.readSnapshot(
          1,
          new DataInputDeserializer(snapshotData.getCopyOfBuffer),
          Thread.currentThread().getContextClassLoader
        )
        snapshot.restoreSerializer()
      }
    )
  }

  test("can be reconfigured from old snapshot") {
    testRoundTrips(
      List(testRecord1, testRecord2, testRecord3),
      { s =>
        val snapshot = s.snapshotConfiguration()
        val compatibility =
          new GenericRecordWithSchemaIdSerializer().snapshotConfiguration().resolveSchemaCompatibility(snapshot)
        compatibility.isCompatibleWithReconfiguredSerializer shouldBe true
        compatibility.getReconfiguredSerializer
      }
    )
  }

  test("can be reconfigured when empty snapshot is used") {
    testRoundTrips(
      List(testRecord1, testRecord2, testRecord3),
      { s =>
        val compatibility =
          s.snapshotConfiguration().resolveSchemaCompatibility(new GenericRecordWithSchemaIdSerializerSnapshot())
        compatibility.isCompatibleWithReconfiguredSerializer shouldBe true
        compatibility.getReconfiguredSerializer
      }
    )
  }

  private def testRoundTrips(
      records: Iterable[GenericRecordWithSchemaId],
      copy: GenericRecordWithSchemaIdSerializer => TypeSerializer[GenericRecordWithSchemaId] = { s => s }
  ): Unit = {
    records.zipWithIndex.foreach { case (record, index) =>
      withClue(s"record #$index") {
        val serializer = new GenericRecordWithSchemaIdSerializer()
        serializeAndDeserialize(serializer, record, copy)
      }
    }

    val serializer = new GenericRecordWithSchemaIdSerializer()
    records.zipWithIndex.foreach { case (record, index) =>
      withClue(s"already seen records: $index") {
        serializeAndDeserialize(serializer, record, copy)
      }
    }
  }

  private def serialize(
      serializer: TypeSerializer[GenericRecordWithSchemaId],
      record: GenericRecordWithSchemaId
  ): Array[Byte] = {
    val output = new DataOutputSerializer(100)
    serializer.serialize(record, output)
    output.getCopyOfBuffer
  }

  private def deserialize(
      serializer: TypeSerializer[GenericRecordWithSchemaId],
      data: Array[Byte]
  ): GenericRecordWithSchemaId = {
    serializer.deserialize(new DataInputDeserializer(data))
  }

  private def serializeAndDeserialize(
      serializer: GenericRecordWithSchemaIdSerializer,
      record: GenericRecordWithSchemaId,
      copySerializer: GenericRecordWithSchemaIdSerializer => TypeSerializer[GenericRecordWithSchemaId]
  ): Unit = {
    val recordBytes = serialize(serializer, record)

    val deserializer = copySerializer(serializer)
    serializer shouldBe deserializer
    System.identityHashCode(serializer) !== System.identityHashCode(deserializer)

    deserialize(deserializer, recordBytes) shouldBe record
  }

}
