package pl.touk.nussknacker.engine.flink.api.typeinfo;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.serialization.SerializerConfig;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.base.ListSerializer;
import org.apache.flink.api.java.typeutils.runtime.NullableSerializer;

import java.util.List;
import java.util.Objects;

/**
 * A {@link TypeInformation} for the list types of the Java API, accepting null collection and null
 * elements.
 *
 * It's a modified copy of solution from Flink 2.0+
 *
 * @param <T> The type of the elements in the list.
 */
@PublicEvolving
public class NullableListTypeInfo<T> extends TypeInformation<List<T>> {

    private final TypeInformation<T> elementTypeInfo;

    public NullableListTypeInfo(Class<T> elementTypeClass) {
        this(TypeInformation.of(Objects.requireNonNull(elementTypeClass)));
    }

    public NullableListTypeInfo(TypeInformation<T> elementTypeInfo) {
        this.elementTypeInfo = Objects.requireNonNull(elementTypeInfo);
    }

    public TypeInformation<T> getElementTypeInfo() {
        return elementTypeInfo;
    }

    @Override
    public boolean isBasicType() {
        return false;
    }

    @Override
    public boolean isTupleType() {
        return false;
    }

    @Override
    public int getArity() {
        return 0;
    }

    @Override
    public int getTotalFields() {
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<T>> getTypeClass() {
        return (Class<List<T>>) (Class<?>) List.class;
    }

    @Override
    public boolean isKeyType() {
        return false;
    }

    @Override
    public TypeSerializer<List<T>> createSerializer(SerializerConfig config) {
        TypeSerializer<T> elementTypeSerializer =
                NullableSerializer.wrap(elementTypeInfo.createSerializer(config), false);
        ListSerializer<T> listSerializer = new ListSerializer<>(elementTypeSerializer);
        return NullableSerializer.wrap(listSerializer, false);
    }

    @Override
    public TypeSerializer<List<T>> createSerializer(ExecutionConfig config) {
        return createSerializer(config.getSerializerConfig());
    }

    @Override
    public String toString() {
        return "NullableList<" + elementTypeInfo + '>';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NullableListTypeInfo) {
            NullableListTypeInfo<?> other = (NullableListTypeInfo<?>) obj;
            return other.canEqual(this) && elementTypeInfo.equals(other.elementTypeInfo);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * elementTypeInfo.hashCode() + 1;
    }

    @Override
    public boolean canEqual(Object obj) {
        return obj instanceof NullableListTypeInfo;
    }
}
