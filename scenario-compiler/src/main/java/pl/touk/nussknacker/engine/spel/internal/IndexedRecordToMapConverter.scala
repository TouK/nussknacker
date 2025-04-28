package pl.touk.nussknacker.engine.spel.internal;

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
      // this is how it would look if we could import IndexedRecord class
//      val r = source.asInstanceOf[IndexedRecord];
//      r.getSchema.getFields.asScala.map(n => n.name() -> r.get(n.pos())).toMap.asJava

      // source is of type org.apache.avro.generic.IndexedRecord

      // schema is of type org.apache.avro.Schema
      val schema = invokeReflectiveMethodWithNoParameters(source, "getSchema")
      // fields is of type List[org.apache.avro.Schema.Field]
      val fields = invokeReflectiveMethodWithNoParameters(schema, "getFields").asInstanceOf[util.List[_]]
      fields.asScala
        .map(n => {
          val name = invokeReflectiveMethodWithNoParameters(n, "name").asInstanceOf[String]
          val pos  = invokeReflectiveMethodWithNoParameters(n, "pos").asInstanceOf[Int]

          val getMethod = source.getClass.getDeclaredMethod("get", classOf[Int])
          val value     = getMethod.invoke(source, pos.asInstanceOf[AnyRef])
          name -> value
        })
        .toMap
        .asJava
    }
  }

  private def invokeReflectiveMethodWithNoParameters(target: Any, methodName: String): Any = {
    val method = target.getClass.getMethod(methodName)
    method.setAccessible(true)
    method.invoke(target)
  }

}
