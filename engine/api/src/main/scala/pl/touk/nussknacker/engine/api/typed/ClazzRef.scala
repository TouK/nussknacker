package pl.touk.nussknacker.engine.api.typed

import io.circe.Encoder
import pl.touk.nussknacker.engine.api.ArgonautCirce

import scala.reflect.ClassTag

object ClazzRef {

  implicit val encoder: Encoder[ClazzRef] = ArgonautCirce.toEncoder(TypeEncoders.clazzRefEncoder)

  def unknown: ClazzRef = ClazzRef[Any]

  def apply[T: ClassTag]: ClazzRef = {
    ClazzRef(implicitly[ClassTag[T]].runtimeClass)
  }

  def apply(clazz: Class[_], params: List[ClazzRef] = Nil): ClazzRef = {
    ClazzRef(clazz.getName, clazz, params)
  }
}

case class ClazzRef(refClazzName: String, clazz: Class[_], params: List[ClazzRef])