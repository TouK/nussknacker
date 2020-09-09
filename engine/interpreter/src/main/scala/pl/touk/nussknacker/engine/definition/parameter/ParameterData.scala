package pl.touk.nussknacker.engine.definition.parameter

import java.lang.annotation.Annotation
import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.reflect.ClassTag

trait ParameterData {

  def typing: TypingResult

  def getAnnotation[T<:Annotation:ClassTag]: Option[T]

}

object ParameterData {

  def apply(parameter: Parameter, givenT: TypingResult): ParameterData = new ParameterData {

    override def typing: TypingResult = givenT

    override def getAnnotation[T<:Annotation:ClassTag]: Option[T] =
      Option(parameter.getAnnotation(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]))
  }
}
