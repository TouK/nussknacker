package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.definition.{NotEmptyValidator, ParameterValidator}

object NotEmptyValidatorEvaluator extends ValidatorEvaluator {

  override def evaluate(param: Parameter): Option[ParameterValidator] = {
    param.getAnnotation(classOf[Nullable]) match {
      case _: Nullable => None
      case _ => Some(NotEmptyValidator)
    }
  }
}
