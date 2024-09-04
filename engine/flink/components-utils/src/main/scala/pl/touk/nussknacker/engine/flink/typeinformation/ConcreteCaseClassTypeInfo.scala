package pl.touk.nussknacker.engine.flink.typeinformation

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import pl.touk.nussknacker.engine.flink.api.typeinfo.caseclass.{CaseClassTypeInfo, ScalaCaseClassSerializer}

import scala.reflect.{ClassTag, classTag}

class ConcreteCaseClassTypeInfo[T <: Product](cls: Class[T], fields: List[(String, TypeInformation[_])])
    extends CaseClassTypeInfo[T](cls, Array.empty, fields.map(_._2), fields.map(_._1)) {

  @silent("deprecated")
  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] = {
    new ScalaCaseClassSerializer[T](cls, fields.map(_._2.createSerializer(config)).toArray)
  }

}

object ConcreteCaseClassTypeInfo {
  def apply[T <: Product](cls: Class[T], fields: (String, TypeInformation[_])*): ConcreteCaseClassTypeInfo[T] =
    new ConcreteCaseClassTypeInfo[T](cls, fields.toList)

  def apply[T <: Product: ClassTag](fields: (String, TypeInformation[_])*): ConcreteCaseClassTypeInfo[T] = {
    apply(classTag[T].runtimeClass.asInstanceOf[Class[T]], fields: _*)
  }

}
