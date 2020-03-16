package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.ParameterValidator
import pl.touk.nussknacker.engine.api.validation.{Literal, LiteralType}

case class LiteralValidatorExtractor(literalType: LiteralType, parameterValidator: ParameterValidator) extends ValidatorExtractor {
  override def extract(param: Parameter): Option[ParameterValidator] =
    param.getAnnotation(classOf[Literal]) match {
      case annotation: Literal if annotation.`type`() == literalType => Some(parameterValidator)
      case _ => None
    }
}
