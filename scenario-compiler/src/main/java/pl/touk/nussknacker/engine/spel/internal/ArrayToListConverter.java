package pl.touk.nussknacker.engine.spel.internal;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.lang.Nullable;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Set;

final class ArrayToListConverter implements ConditionalGenericConverter {

	private final ConversionService conversionService;


	public ArrayToListConverter(ConversionService conversionService) {
		this.conversionService = conversionService;
	}


	@Override
	public Set<ConvertiblePair> getConvertibleTypes() {
		return Set.of(
            new ConvertiblePair(Object[].class, Iterable.class),
            new ConvertiblePair(Object[].class, Collection.class),
            new ConvertiblePair(Object[].class, List.class)
        );
	}

	@Override
	public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
		return ConversionUtils.canConvertElements(
            sourceType.getElementTypeDescriptor(),
            targetType.getElementTypeDescriptor(),
            conversionService
        );
	}

	@Override
	@Nullable
	public Object convert(
        @Nullable Object source,
        TypeDescriptor sourceTypeDescriptor,
        TypeDescriptor targetTypeDescriptor
    ) {
		if (source == null) {
			return null;
		}
		int arrayLength = Array.getLength(source);
		TypeDescriptor elementTypeDescriptor = targetTypeDescriptor.getElementTypeDescriptor();
		Collection<Object> result = CollectionFactory.createCollection(
            List.class,
            elementTypeDescriptor != null ? elementTypeDescriptor.getType() : null,
            arrayLength
        );
        for (int i = 0; i < arrayLength; i++) {
            Object sourceElement = Array.get(source, i);
            result.add(sourceElement);
        }
		return result;
	}

}
