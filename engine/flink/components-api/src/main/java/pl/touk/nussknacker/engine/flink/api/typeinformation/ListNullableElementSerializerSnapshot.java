package pl.touk.nussknacker.engine.flink.api.typeinformation;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;

import java.util.List;

/** Snapshot for {@link ListNullableElementSerializer}. */
@Internal
public class ListNullableElementSerializerSnapshot<T>
        extends CompositeTypeSerializerSnapshot<List<T>, ListNullableElementSerializer<T>> {

    private static final int CURRENT_VERSION = 1;

    public ListNullableElementSerializerSnapshot() {}

    /** Constructor to create the snapshot for writing. */
    public ListNullableElementSerializerSnapshot(ListNullableElementSerializer<T> serializerInstance) {
        super(serializerInstance);
    }

    @Override
    protected int getCurrentOuterSnapshotVersion() {
        return CURRENT_VERSION;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ListNullableElementSerializer<T> createOuterSerializerWithNestedSerializers(
            TypeSerializer<?>[] nestedSerializers) {
        return new ListNullableElementSerializer<>((TypeSerializer<T>) nestedSerializers[0]);
    }

    @Override
    protected TypeSerializer<?>[] getNestedSerializers(ListNullableElementSerializer<T> outerSerializer) {
        return new TypeSerializer<?>[] { outerSerializer.elementSerializer() };
    }

}
