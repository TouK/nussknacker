package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils

import scala.reflect.ClassTag

object ClazzRef {

  def unknown: ClazzRef = ClazzRef[Any]

  def apply[T: ClassTag]: ClazzRef = {
    ClazzRef(implicitly[ClassTag[T]].runtimeClass)
  }

  def apply(clazz: Class[_], params: List[ClazzRef] = Nil): ClazzRef = {
    ClazzRef(clazz.getName, clazz, params)
  }

  import argonaut._
  import Argonaut._

  implicit val encode: EncodeJson[ClazzRef] = EncodeJson[ClazzRef](ref => jObjectFields("refClazzName" -> jString(ref.refClazzName)))

  implicit val decode: DecodeJson[ClazzRef] = DecodeJson[ClazzRef](marshalled => {
    (marshalled --\ "refClazzName")
      .focus
      .flatMap(_.string)
      .map(k => ClassUtils.getClass(getClass.getClassLoader, k))
      .map(ClazzRef(_))
      .fold(DecodeResult.fail[ClazzRef]("No class ref", marshalled.history))(DecodeResult.ok)
  })




}

case class ClazzRef(refClazzName: String, clazz: Class[_], params: List[ClazzRef]) {
  def toClass(classLoader: ClassLoader): Class[_] = clazz
}