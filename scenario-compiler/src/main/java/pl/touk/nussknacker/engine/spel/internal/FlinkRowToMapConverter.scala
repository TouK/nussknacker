package pl.touk.nussknacker.engine.spel.internal

import org.springframework.core.convert.{ConversionService, TypeDescriptor}
import org.springframework.core.convert.converter.{ConditionalGenericConverter, GenericConverter}
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.util.AssignabilityUtil

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

// Converts org.apache.flink.types.Row to Map. We should not have library containing org.apache.flink.types.Row class as dependency here
class FlinkRowToMapConverter(val conversionService: ConversionService) extends ConditionalGenericConverter {

  override def getConvertibleTypes: util.Set[GenericConverter.ConvertiblePair] =
    Collections.singleton(new ConvertiblePair(classOf[Object], classOf[util.Map[_, _]]))

  override def matches(sourceType: TypeDescriptor, targetType: TypeDescriptor): Boolean =
    AssignabilityUtil.isAssignableToLoadableClass(
      sourceType.getObjectType,
      "org.apache.flink.types.Row"
    ) && ClassUtils.isAssignable(targetType.getObjectType, classOf[util.Map[_, _]])

  override def convert(source: Any, sourceType: TypeDescriptor, targetType: TypeDescriptor): AnyRef = {
    if (source == null) {
      null
    } else {
      // if we could import Row class this would look like this:
//            val r      = source.asInstanceOf[org.apache.flink.types.Row]
//            val fields = r.getFieldNames(true).asScala
//            fields.map(e => e -> r.getField(e)).toMap.asJava
      val getFieldsMethod = source.getClass.getMethod("getFieldNames", classOf[Boolean])
      getFieldsMethod.setAccessible(true)
      val fields = getFieldsMethod.invoke(source, true).asInstanceOf[java.util.Set[String]].asScala
      fields
        .map(e => {
          val getFieldMethod = source.getClass.getMethod("getField", classOf[String])
          getFieldMethod.setAccessible(true)
          e -> getFieldMethod.invoke(source, e)
        })
        .toMap
        .asJava
    }
  }

}
