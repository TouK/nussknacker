package pl.touk.nussknacker.engine.definition.component.parameter

import pl.touk.nussknacker.engine.api.{ParameterSection => ApiParameterSection, ParameterSectionType}
import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.definition.ParameterSection

object ParameterSectionExtractor {

  def extract(param: ParameterData, parameterConfig: ParameterConfig): Option[ParameterSection] =
    parameterConfig.section
      .orElse(extractFromAnnotation(param))

  private def extractFromAnnotation(param: ParameterData): Option[ParameterSection] = param
    .getAnnotation[ApiParameterSection]
    .map(section => parameterSection(section))

  private def parameterSection(section: ApiParameterSection): ParameterSection =
    if (section.`type`() == ParameterSectionType.STANDARD) ParameterSection.Standard
    else ParameterSection.Additional

}
