package pl.touk.nussknacker.engine.definition.validator

import java.util.Optional

import javax.annotation.Nullable
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}

object MandatoryValidatorExtractor extends ValidatorExtractor {

  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    if (params.isScalaOptionParameter || params.isJavaOptionalParameter) {
      None
    } else if (params.rawJavaParam.getAnnotation(classOf[Nullable]) != null) {
      None
    } else {
      Some(MandatoryParameterValidator())
    }
  }

}
