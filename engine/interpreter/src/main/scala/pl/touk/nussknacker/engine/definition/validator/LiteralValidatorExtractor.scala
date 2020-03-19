package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{LiteralParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.Literal

object LiteralValidatorExtractor extends ValidatorExtractor {
  override def extract(param: Parameter): Option[ParameterValidator] =
    param.getAnnotation(classOf[Literal]) match {
      case _: Literal => param.getType match {
        case clazz => LiteralParameterValidator(clazz)
        case _ => None
      }
      case _ => None
    }
}
