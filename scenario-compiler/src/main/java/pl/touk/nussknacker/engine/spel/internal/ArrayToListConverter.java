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
	public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (source == null) {
			return null;
		}
		int length = Array.getLength(source);
		TypeDescriptor elementDesc = targetType.getElementTypeDescriptor();
		Collection<Object> target = CollectionFactory.createCollection(List.class,
				(elementDesc != null ? elementDesc.getType() : null), length);
        for (int i = 0; i < length; i++) {
            Object sourceElement = Array.get(source, i);
            target.add(sourceElement);
        }
		return target;
	}

}
