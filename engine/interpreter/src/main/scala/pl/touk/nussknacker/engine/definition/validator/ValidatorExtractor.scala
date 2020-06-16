package pl.touk.nussknacker.engine.definition.validator

import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait ValidatorExtractor {

  def extract(params: ValidatorExtractorParameters): Option[ParameterValidator]

}

case class ValidatorExtractorParameters(rawJavaParam: java.lang.reflect.Parameter,
                                        paramType: TypingResult,
                                        isRequiredParameter: Boolean,
                                        isScalaOptionParameter: Boolean,
                                        isJavaOptionalParameter: Boolean,
                                        extractedEditor: Option[ParameterEditor])
