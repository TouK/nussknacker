package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.NotBlank
import pl.touk.nussknacker.engine.api.definition.{NotBlankValueValidator, ParameterValidator}
object NotBlankValueValidatorExtractor extends ValidatorExtractor {

  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param if param.getAnnotation(classOf[NotBlank]) != null => Some(NotBlankValueValidator)
      case _ =>  None
    }
  }
}
