package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{LiteralIntValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.Literal

object LiteralValidatorExtractor extends ValidatorExtractor {
  override def extract(param: Parameter): Option[ParameterValidator] =
    param.getAnnotation(classOf[Literal]) match {
      case _:Literal => param.getType match {
        case clazz if clazz == classOf[Int] => Some(LiteralIntValidator)
        case clazz if clazz == classOf[Integer] => Some(LiteralIntValidator)
        case _ => None
      }
      case _ => None
    }
}
