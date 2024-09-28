package pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{TypeInfoFactory, TypeInformation}
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.typeutils.runtime.NullableSerializer
import pl.touk.nussknacker.engine.flink.api.typeinfo.option.OptionTypeInfo

import java.lang.reflect.Type
import scala.reflect._
import scala.reflect.runtime.universe._

// Generic class factory for creating CaseClassTypeInfo
abstract class CaseClassTypeInfoFactory[T <: Product: ClassTag] extends TypeInfoFactory[T] with Serializable {

  override def createTypeInfo(
      t: Type,
      genericParameters: java.util.Map[String, TypeInformation[_]]
  ): TypeInformation[T] = {
    val runtimeClassType         = classTag[T].runtimeClass
    val (fieldNames, fieldTypes) = getClassFieldsInfo(runtimeClassType)
    val classType                = runtimeClassType.asInstanceOf[Class[T]]
    new CaseClassTypeInfo[T](classType, Array.empty, fieldTypes.toIndexedSeq, fieldNames) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[T] = {
        new ScalaCaseClassSerializer[T](
          classType,
          fieldTypes.map(typeInfo => NullableSerializer.wrap(typeInfo.createSerializer(config), true)).toArray
        )
      }
    }
  }

  private def getClassFieldsInfo(runtimeClassType: Class[_]): (List[String], List[TypeInformation[_]]) = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val fields = mirror
      .classSymbol(runtimeClassType)
      .primaryConstructor
      .asMethod
      .paramLists
      .head
    val fieldNames = fields.map(_.name.decodedName.toString)
    val fieldTypes = fields.map { field =>
      val fieldClass = mirror.runtimeClass(field.typeSignature)

      if (classOf[Option[_]].isAssignableFrom(fieldClass)) {
        val optionTypeClass = mirror.runtimeClass(field.typeSignature.typeArgs.head)
        new OptionTypeInfo(TypeExtractor.getForClass(optionTypeClass))
      } else {
        TypeExtractor.getForClass(fieldClass)
      }
    }
    (fieldNames, fieldTypes)
  }

}
