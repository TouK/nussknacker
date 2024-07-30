package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

protected object OptionalityBasedDefaultValueDeterminer
    extends ParameterDefaultValueDeterminer
    with SimpleLanguageDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] =
    Option(parameters).filter(_.isOptional).flatMap { _ =>
      calculateDefaultValue(parameters.determinedEditor, Some(""))
    }

}
