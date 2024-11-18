package pl.touk.nussknacker.engine.schemedkafka.flink.typeinfo

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.{RecordHeader, RecordHeaders}
import org.apache.kafka.common.record.TimestampType

import java.util.{Objects, Optional}

class ConsumerRecordTypeInfo[K, V](val keyTypeInfo: TypeInformation[K], val valueTypeInfo: TypeInformation[V])
    extends TypeInformation[ConsumerRecord[K, V]] {

  override def getTypeClass: Class[ConsumerRecord[K, V]] = classOf[ConsumerRecord[K, V]]

  @silent("deprecated")
  override def createSerializer(
      config: org.apache.flink.api.common.ExecutionConfig
  ): TypeSerializer[ConsumerRecord[K, V]] = {
    new ConsumerRecordSerializer[K, V](keyTypeInfo.createSerializer(config), valueTypeInfo.createSerializer(config))
  }

  // ConsumerRecord 8 simple fields
  override def getArity: Int = 8

  // TODO: find out what's the correct value here
  // ConsumerRecord 8 fields (w/o: headers, key, value) + Headers 2 fields + key.fields + value.fields
  override def getTotalFields: Int = 8 + 2 + keyTypeInfo.getTotalFields + valueTypeInfo.getTotalFields

  override def isKeyType: Boolean = false

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def toString: String =
    s"ConsumerRecordTypeInfo($keyTypeInfo, $valueTypeInfo)"

  override def canEqual(obj: Any): Boolean =
    obj.isInstanceOf[ConsumerRecordTypeInfo[_, _]]

  override def equals(obj: Any): Boolean =
    obj match {
      case info: ConsumerRecordTypeInfo[_, _] =>
        keyTypeInfo.equals(info.keyTypeInfo) && valueTypeInfo.equals(info.valueTypeInfo)
      case _ => false
    }

  override def hashCode(): Int =
    Objects.hashCode(keyTypeInfo, valueTypeInfo)
}

class ConsumerRecordSerializer[K, V](val keySerializer: TypeSerializer[K], val valueSerializer: TypeSerializer[V])
    extends TypeSerializer[ConsumerRecord[K, V]] {

  override def getLength: Int = -1

  override def isImmutableType: Boolean = true

  override def createInstance(): ConsumerRecord[K, V] =
    new ConsumerRecord[K, V](null, 0, 0, null.asInstanceOf[K], null.asInstanceOf[V])

  override def duplicate(): TypeSerializer[ConsumerRecord[K, V]] = {
    val keyDuplicated   = keySerializer.duplicate()
    val valueDuplicated = valueSerializer.duplicate()

    if (keyDuplicated.equals(keySerializer) && valueDuplicated.equals(valueSerializer)) {
      this
    } else {
      new ConsumerRecordSerializer(keyDuplicated, valueDuplicated)
    }
  }

  override def copy(record: ConsumerRecord[K, V]): ConsumerRecord[K, V] =
    new ConsumerRecord[K, V](
      record.topic(),
      record.partition(),
      record.offset(),
      record.timestamp(),
      record.timestampType(),
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      record.key(),
      record.value(),
      record.headers(),
      record.leaderEpoch()
    )

  override def copy(record: ConsumerRecord[K, V], reuse: ConsumerRecord[K, V]): ConsumerRecord[K, V] =
    copy(record)

  override def copy(source: DataInputView, target: DataOutputView): Unit =
    serialize(deserialize(source), target)

  override def serialize(record: ConsumerRecord[K, V], target: DataOutputView): Unit = {
    target.writeUTF(record.topic())
    target.writeInt(record.partition())
    target.writeLong(record.offset())
    target.writeLong(record.timestamp())

    // Short takes less space than int
    target.writeShort(record.timestampType().id)

    target.writeInt(record.serializedKeySize())
    target.writeInt(record.serializedValueSize())

    // Serialize the key (can be null)
    if (record.key() == null) {
      target.writeBoolean(false)
    } else {
      target.writeBoolean(true)
      keySerializer.serialize(record.key(), target)
    }

    // Serialize the value (can be null)
    if (record.value() == null) {
      target.writeBoolean(false)
    } else {
      target.writeBoolean(true)
      valueSerializer.serialize(record.value(), target)
    }

    if (record.leaderEpoch().isPresent) {
      target.writeBoolean(true)
      target.writeInt(record.leaderEpoch.get())
    } else {
      target.writeBoolean(false)
    }

    target.writeInt(record.headers().toArray.length)
    record.headers().forEach { header =>
      target.writeUTF(header.key())
      target.writeInt(header.value().length)
      target.write(header.value())
    }
  }

  override def deserialize(reuse: ConsumerRecord[K, V], source: DataInputView): ConsumerRecord[K, V] =
    deserialize(source)

  override def deserialize(source: DataInputView): ConsumerRecord[K, V] = {
    val topic               = source.readUTF()
    val partition           = source.readInt()
    val offset              = source.readLong()
    val timestamp           = source.readLong()
    val timestampTypeId     = source.readShort().toInt
    val serializedKeySize   = source.readInt()
    val serializedValueSize = source.readInt()

    val key         = if (source.readBoolean()) keySerializer.deserialize(source) else null.asInstanceOf[K]
    val value       = if (source.readBoolean()) valueSerializer.deserialize(source) else null.asInstanceOf[V]
    val leaderEpoch = if (source.readBoolean()) Optional.of[Integer](source.readInt()) else Optional.empty[Integer]

    val headers = (0 until source.readInt()).foldLeft(new RecordHeaders) { (headers, _) =>
      val name = source.readUTF()
      val len  = source.readInt()

      val value = new Array[Byte](len)
      source.read(value)

      val header = new RecordHeader(name, value)
      headers.add(header)
      headers
    }

    val timestampType =
      TimestampType
        .values()
        .toList
        .find(_.id == timestampTypeId)
        .getOrElse(throw new IllegalArgumentException(s"Unknown TimestampType id: $timestampTypeId."))

    new ConsumerRecord[K, V](
      topic,
      partition,
      offset,
      timestamp,
      timestampType,
      serializedKeySize,
      serializedValueSize,
      key,
      value,
      headers,
      leaderEpoch
    )
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[ConsumerRecord[K, V]] =
    new ConsumerRecordTypeSerializerSnapshot()

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ConsumerRecordSerializer[_, _] =>
        keySerializer.equals(other.keySerializer) && valueSerializer.equals(other.valueSerializer)
      case _ => false
    }
  }

  override def hashCode(): Int =
    Objects.hashCode(keySerializer, valueSerializer)

}
