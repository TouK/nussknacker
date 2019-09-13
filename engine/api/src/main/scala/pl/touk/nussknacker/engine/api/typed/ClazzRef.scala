package pl.touk.nussknacker.engine.api.typed

import io.circe.Encoder
import pl.touk.nussknacker.engine.api.ArgonautCirce

import scala.reflect.runtime.universe._

object ClazzRef {

  implicit val encoder: Encoder[ClazzRef] = ArgonautCirce.toEncoder(TypeEncoders.clazzRefEncoder)

  def unknown: ClazzRef = ClazzRef[Any]

  def apply[T: TypeTag]: ClazzRef = {
    val tag = typeTag[T]
    // is it correct mirror?
    implicit val mirror: Mirror = tag.mirror
    fromType(tag.tpe)
  }

  private def fromType(typ: Type)(implicit mirror: Mirror): ClazzRef = {
    val runtimeClass = mirror.runtimeClass(typ.erasure)
    ClazzRef(runtimeClass, typ.typeArgs.map(fromType))
  }

  def apply(clazz: Class[_]): ClazzRef = {
    ClazzRef(clazz, Nil)
  }

  def apply(clazz: Class[_], params: List[ClazzRef]): ClazzRef = {
    ClazzRef(clazz.getName, clazz, params)
  }

}

case class ClazzRef private(refClazzName: String, clazz: Class[_], params: List[ClazzRef])