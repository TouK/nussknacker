package pl.touk.nussknacker.engine.definition.component.parameter

import pl.touk.nussknacker.engine.api.{ParameterCategory => ApiParameterCategory, ParameterCategoryType}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.ParameterCategory

object ParameterCategoryExtractor {

  def extract(param: ParameterData, parameterConfig: ParameterConfig): Option[ParameterCategory] =
    parameterConfig.category
      .orElse(extractFromAnnotation(param))

  private def extractFromAnnotation(param: ParameterData): Option[ParameterCategory] = param
    .getAnnotation[ApiParameterCategory]
    .map(section => parameterCategory(section))

  private def parameterCategory(category: ApiParameterCategory): ParameterCategory =
    if (category.`type`() == ParameterCategoryType.STANDARD) ParameterCategory.Standard
    else ParameterCategory.Advanced

}
