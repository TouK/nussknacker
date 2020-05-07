package pl.touk.nussknacker.engine.definition.validator

import java.lang.reflect.Parameter

import javax.validation.constraints.{Max, Min}
import pl.touk.nussknacker.engine.api.definition.{MaximalNumberValidator, MinimalNumberValidator, ParameterValidator}

object SingleValueAnnotationValidatorExtractor extends ValidatorExtractor {
  override def extract(params: ValidatorExtractorParameters): Option[ParameterValidator] = {
    params.rawJavaParam match {
      case param if param.getAnnotation(classOf[Min]) != null => extractMinimalNumberValidator(param)
      case param if param.getAnnotation(classOf[Max]) != null => extractMaximalNumberValidator(param)
      case _ => None
    }
  }

  private def extractMinimalNumberValidator(param: Parameter) = {
    val annotation = param.getAnnotation(classOf[Min])

    Some(MinimalNumberValidator(annotation.value(), annotation.message()))
  }

  private def extractMaximalNumberValidator(param: Parameter) = {
    val annotation = param.getAnnotation(classOf[Max])

    Some(MaximalNumberValidator(annotation.value(), annotation.message()))
  }

}