package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.Min
import pl.touk.nussknacker.engine.api.definition.{MinimalNumberValidator, ParameterValidator}

object SingleValueAnnotationValidatorExtractor extends ValidatorExtractor {
  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param => matchMinimalNumberValidator(param)
      case _ => None
    }
  }

  private def matchMinimalNumberValidator(param: Parameter) = {
    param.getAnnotation(classOf[Min]) match {
      case annotation: Min => Some(MinimalNumberValidator(annotation.value(), annotation.message()));
      case _ => None
    }
  }
}