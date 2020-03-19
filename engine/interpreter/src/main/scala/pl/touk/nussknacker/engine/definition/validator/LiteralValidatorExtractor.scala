package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition.{LiteralParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.Literal

/**
  * A now we support only simple literals like: Integer / String.
  * Option / Optional / LazyParam are not allowed.
  */
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
