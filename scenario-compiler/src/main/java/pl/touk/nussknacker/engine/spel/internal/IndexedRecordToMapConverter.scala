package pl.touk.nussknacker.engine.spel.internal;

import org.apache.avro.generic.IndexedRecord
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.{ConditionalGenericConverter, GenericConverter}
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.util.AssignabilityUtil

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

// Converts org.apache.avro.generic.IndexedRecord to Map. We should not have library containing IndexedRecord as dependency here
class IndexedRecordToMapConverter(val conversionService: ConversionService) extends ConditionalGenericConverter {

  override def getConvertibleTypes: util.Set[GenericConverter.ConvertiblePair] =
    Collections.singleton(new ConvertiblePair(classOf[Object], classOf[util.Map[_, _]]))

  override def matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean =
    AssignabilityUtil.isAssignableToLoadableClass(
      sourceType.getObjectType,
      "org.apache.avro.generic.IndexedRecord"
    ) && ClassUtils.isAssignable(targetType.getObjectType, classOf[util.Map[_, _]])

  override def convert(source: Any, sourceType: TypeDescriptor, targetType: TypeDescriptor): AnyRef = {
    if (source == null) {
      null
    } else {
      // TODO_PAWEL avoid using the static link
      val r = source.asInstanceOf[IndexedRecord];
      r.getSchema.getFields.asScala.map(n => n.name() -> r.get(n.pos())).toMap.asJava

    }

  }

}
