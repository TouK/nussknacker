package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{LiteralParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.validation.Literal

object LiteralValidatorExtractor extends ValidatorExtractor {
  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] =
    params.rawJavaParam.getAnnotation(classOf[Literal]) match {
      case _: Literal => LiteralParameterValidator(params.paramType)
      case _ => None
    }
}
