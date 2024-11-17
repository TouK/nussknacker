package pl.touk.nussknacker.engine.schemdkafka.flink.typeinfo;

import org.apache.flink.api.common.typeutils.CompositeTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import pl.touk.nussknacker.engine.schemedkafka.flink.typeinfo.ConsumerRecordSerializer;
import pl.touk.nussknacker.engine.schemedkafka.flink.typeinfo.ConsumerRecordTypeInfo;

/**
 * A {@link TypeSerializerSnapshot} for the Scala {@link ConsumerRecordTypeInfo}.
 */
public final class ConsumerRecordTypeSerializerSnapshot<K, V>
  extends CompositeTypeSerializerSnapshot<ConsumerRecord<K, V>, ConsumerRecordSerializer<K, V>> {

  final private static int VERSION = 1;

  public ConsumerRecordTypeSerializerSnapshot() {
    super();
  }

  public ConsumerRecordTypeSerializerSnapshot(ConsumerRecordSerializer<K, V> serializerInstance) {
    super(serializerInstance);
  }

  @Override
  protected int getCurrentOuterSnapshotVersion() {
    return VERSION;
  }

  @Override
  protected TypeSerializer<?>[] getNestedSerializers(ConsumerRecordSerializer<K, V> outerSerializer) {
    return new TypeSerializer[] { outerSerializer.keySerializer(), outerSerializer.valueSerializer() };
  }

  @Override
  protected ConsumerRecordSerializer<K, V> createOuterSerializerWithNestedSerializers(TypeSerializer<?>[] nestedSerializers) {
    @SuppressWarnings("unchecked")
    TypeSerializer<K> keySerializer = (TypeSerializer<K>) nestedSerializers[0];

    @SuppressWarnings("unchecked")
    TypeSerializer<V> valueSerializer = (TypeSerializer<V>) nestedSerializers[1];

    return new ConsumerRecordSerializer<>(keySerializer, valueSerializer);
  }
}
