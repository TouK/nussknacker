package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.graph.expression.Expression

object ConfigParameterDefaultValueDeterminer extends ParameterDefaultValueDeterminer with LazyLogging {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    val defaultValue = parameters.parameterConfig.defaultValue
    val languagesFromEditors =
      parameters.determinedEditors.map(editor => EditorBasedLanguageDeterminer.determineLanguageOf(Some(editor))).toSet
    defaultValue match {
      case Some(value) if !languagesFromEditors.contains(value.language) =>
        logger.warn(
          s"The default value language ${value.language} does not match editor languages: ${languagesFromEditors.mkString(",")}"
        )
      case Some(_) | None =>
    }

    defaultValue
  }

}
