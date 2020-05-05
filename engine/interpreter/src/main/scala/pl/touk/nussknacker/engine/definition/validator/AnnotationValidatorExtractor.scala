package pl.touk.nussknacker.engine.definition.validator

import java.lang.annotation.Annotation

import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import scala.reflect.ClassTag

class AnnotationValidatorExtractor(annotationClass: Class[_ <: Annotation], parameterValidator: ParameterValidator) extends ValidatorExtractor {
  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    if (params.rawJavaParam.getAnnotation(annotationClass) != null)
      Some(parameterValidator)
    else
      None
  }
}

object AnnotationValidatorExtractor {
  def apply[T <: Annotation : ClassTag](parameterValidator: ParameterValidator): AnnotationValidatorExtractor = {
    val annotationClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    new AnnotationValidatorExtractor(annotationClass, parameterValidator)
  }
}
