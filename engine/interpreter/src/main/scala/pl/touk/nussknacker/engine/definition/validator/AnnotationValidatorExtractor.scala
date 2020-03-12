package pl.touk.nussknacker.engine.definition.validator

import java.lang.annotation.Annotation
import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

import scala.reflect.ClassTag
class AnnotationValidatorExtractor(annotationClass: Class[_ <: Annotation], parameterValidator: ParameterValidator) extends ValidatorExtractor {
  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param if param.getAnnotation(annotationClass) != null => Some(parameterValidator)
      case _ => None
    }
  }
}

object AnnotationValidatorExtractor {
  def apply[T <: Annotation : ClassTag](parameterValidator: ParameterValidator): AnnotationValidatorExtractor = {
    val annotationClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    new AnnotationValidatorExtractor(annotationClass, parameterValidator)
  }
}
