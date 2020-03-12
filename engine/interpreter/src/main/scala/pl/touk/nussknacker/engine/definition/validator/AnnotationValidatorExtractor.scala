package pl.touk.nussknacker.engine.definition.validator

import java.lang.annotation.Annotation
import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

import scala.reflect.ClassTag
class AnnotationValidatorExtractor[AnnotationClass <: Annotation : ClassTag](parameterValidator: ParameterValidator) extends ValidatorExtractor {

  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param if param.getAnnotation(toClass) != null => Some(parameterValidator)
      case _ => None
    }
  }

  protected def toClass: Class[AnnotationClass] =
    implicitly[ClassTag[AnnotationClass]].runtimeClass.asInstanceOf[Class[AnnotationClass]]
}
