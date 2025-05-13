package pl.touk.nussknacker.engine.definition.component.parameter.validator

import pl.touk.nussknacker.engine.api.definition.ParameterValidator

import java.lang.annotation.Annotation
import scala.reflect.ClassTag

class AnnotationValidatorExtractor[T <: Annotation: ClassTag](parameterValidatorProvider: T => ParameterValidator)
    extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.parameterData.getAnnotation[T].map(parameterValidatorProvider)
  }

}

object AnnotationValidatorExtractor {

  def apply[T <: Annotation: ClassTag](
      parameterValidatorProvider: T => ParameterValidator
  ): AnnotationValidatorExtractor[T] =
    new AnnotationValidatorExtractor[T](parameterValidatorProvider)

  def apply[T <: Annotation: ClassTag](parameterValidator: ParameterValidator): AnnotationValidatorExtractor[T] =
    apply[T]((_: T) => parameterValidator)

}
