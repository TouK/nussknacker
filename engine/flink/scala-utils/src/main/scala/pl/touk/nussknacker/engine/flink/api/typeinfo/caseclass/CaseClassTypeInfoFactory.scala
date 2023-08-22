package pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.typeutils.runtime.NullableSerializer

import java.lang.reflect.Type
import scala.reflect._

// Generic class factory for creating CaseClassTypeInfo
abstract class CaseClassTypeInfoFactory[T <: Product : ClassTag] extends TypeInfoFactory[T] with Serializable {
  override def createTypeInfo(t: Type, genericParameters: java.util.Map[String, TypeInformation[_]]): TypeInformation[T] = {
    val tClass = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    val fieldNames = tClass.getDeclaredFields.map(_.getName).toList
    val fieldTypes = tClass.getDeclaredFields.map(_.getType).map(TypeExtractor.getForClass(_))

    new CaseClassTypeInfo[T](tClass, Array.empty, fieldTypes.toIndexedSeq, fieldNames) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[T] = {
        new ScalaCaseClassSerializer[T](tClass, fieldTypes.map(typeInfo => NullableSerializer.wrap(typeInfo.createSerializer(config), true)).toArray)
      }
    }
  }
}
