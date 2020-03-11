package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{NotBlankValueValidator, ParameterValidator}
import pl.touk.nussknacker.engine.definition.validator.adnotation.NotBlank

object NotBlankValueValidatorExtractor extends ValidatorExtractor {

  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param if param.getAnnotation(classOf[NotBlank]) != null => Some(NotBlankValueValidator)
      case _ =>  None
    }
  }
}
