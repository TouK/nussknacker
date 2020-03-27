package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter
import java.util.Optional

import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}

object MandatoryValidatorExtractor extends ValidatorExtractor {

  override def extract(param: Parameter): Option[ParameterValidator] = {
    param match {
      case param if param.getType == classOf[Option[_]] => None
      case param if param.getType == classOf[Optional[_]] => None
      case param if param.getAnnotation(classOf[Nullable]) != null => None
      case _ => Some(MandatoryParameterValidator)
    }
  }
}
