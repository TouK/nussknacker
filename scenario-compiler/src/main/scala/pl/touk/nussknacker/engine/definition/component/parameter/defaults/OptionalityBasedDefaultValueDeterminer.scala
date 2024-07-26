package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.definition.component.parameter.EditorBasedLanguageDeterminer
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    Option(parameters).filter(_.isOptional).map { _ =>
      Expression(EditorBasedLanguageDeterminer.determineLanguageOf(parameters.determinedEditor), "")
    }
  }

}
