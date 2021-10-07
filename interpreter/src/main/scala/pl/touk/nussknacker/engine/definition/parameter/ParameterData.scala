package pl.touk.nussknacker.engine.definition.parameter

import java.lang.annotation.Annotation
import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.reflect.ClassTag

//we extract needed data from java.lang.reflect.Parameter to be able to use it e.g. for subprocess parameters
case class ParameterData(typing: TypingResult, annotations: List[_ <: Annotation]) {

  def getAnnotation[T <: Annotation : ClassTag]: Option[T] = annotations.collectFirst {
    case e: T => e
  }
}

object ParameterData {

  def apply(parameter: Parameter, typing: TypingResult): ParameterData = ParameterData(typing, parameter.getAnnotations.toList)
}
