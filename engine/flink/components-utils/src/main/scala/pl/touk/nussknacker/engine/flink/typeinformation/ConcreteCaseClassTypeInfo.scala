package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.{CaseClassTypeInfo, ScalaCaseClassSerializer}

import scala.annotation.nowarn
import scala.reflect.{classTag, ClassTag}

class ConcreteCaseClassTypeInfo[T <: Product](cls: Class[T], fields: List[(String, TypeInformation[_])])
    extends CaseClassTypeInfo[T](cls, Array.empty, fields.map(_._2), fields.map(_._1)) {

  override def createSerializer(config: SerializerConfig): TypeSerializer[T] = {
    new ScalaCaseClassSerializer[T](cls, fields.map(_._2.createSerializer(config)).toArray)
  }

  // TODO: Remove after upgrade to Flink 2.x
  @nowarn("cat=deprecation")
  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    throw new IllegalAccessException("This method shouldn't be invoked")

}

object ConcreteCaseClassTypeInfo {
  def apply[T <: Product](cls: Class[T], fields: (String, TypeInformation[_])*): ConcreteCaseClassTypeInfo[T] =
    new ConcreteCaseClassTypeInfo[T](cls, fields.toList)

  def apply[T <: Product: ClassTag](fields: (String, TypeInformation[_])*): ConcreteCaseClassTypeInfo[T] = {
    apply(classTag[T].runtimeClass.asInstanceOf[Class[T]], fields: _*)
  }

}
