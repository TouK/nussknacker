package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}

import scala.reflect.ClassTag

class ConcreteCaseClassTypeInfo[T <: Product : ClassTag](fields: List[(String, TypeInformation[_])])
  extends CaseClassTypeInfo[T](classOf[T], Array.empty, fields.map(_._2), fields.map(_._1)) {
  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] = {
    new ScalaCaseClassSerializer[T](classOf[T], fields.map(_._2.createSerializer(config)).toArray)
  }
}

object ConcreteCaseClassTypeInfo {
  def apply[T <: Product : ClassTag](fields: (String, TypeInformation[_])*): ConcreteCaseClassTypeInfo[T] = {
    new ConcreteCaseClassTypeInfo[T](fields.toList)
  }
}