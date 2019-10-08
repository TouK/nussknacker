package pl.touk.nussknacker.engine.api.typed

import io.circe.Encoder
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object ClazzRef {

  implicit val encoder: Encoder[ClazzRef] = TypeEncoders.clazzRefEncoder

  def unknown: ClazzRef = ClazzRef[Any]

  def apply[T: ClassTag]: ClazzRef = {
    apply(implicitly[ClassTag[T]].runtimeClass)
  }

  /*using TypeTag can give better description (with extracted generic parameters), however:
    - in runtime/production we usually don't have TypeTag, as we rely on reflection anyway
    - one should be *very* careful with TypeTag as it degrades performance significantly when on critical path (e.g. SpelExpression.evaluate)
   */
  def fromDetailedType[T: TypeTag]: ClazzRef = {
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