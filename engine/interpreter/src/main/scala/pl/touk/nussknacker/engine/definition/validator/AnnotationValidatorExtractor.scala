package pl.touk.nussknacker.engine.definition.validator

import java.lang.annotation.Annotation

import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.reflect.ClassTag

class AnnotationValidatorExtractor[T <: Annotation : ClassTag](parameterValidatorProvider: T => ParameterValidator) extends ValidatorExtractor {
  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    val annotationClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

    params.rawJavaParam.getAnnotation(annotationClass) match {
      case annotation: T => Some(parameterValidatorProvider(annotation))
      case _ => None
    }
  }
}

object AnnotationValidatorExtractor {
  def apply[T <: Annotation : ClassTag](parameterValidatorProvider: T => ParameterValidator): AnnotationValidatorExtractor[T] = {
    new AnnotationValidatorExtractor[T](parameterValidatorProvider)
  }
}
