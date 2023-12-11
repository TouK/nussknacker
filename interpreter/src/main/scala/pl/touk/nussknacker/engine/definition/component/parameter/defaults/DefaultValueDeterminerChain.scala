package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.graph.expression.Expression

object DefaultValueDeterminerChain
    extends DefaultValueDeterminerChain(
      List(
        ConfigParameterDefaultValueDeterminer,
        AnnotationDefaultValueDeterminer,
        OptionalityBasedDefaultValueDeterminer,
        EditorPossibleValuesBasedDefaultValueDeterminer,
        TypeRelatedParameterValueDeterminer
      )
    )

class DefaultValueDeterminerChain(elements: Iterable[ParameterDefaultValueDeterminer])
    extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    elements.view.flatMap(_.determineParameterDefaultValue(parameters)).headOption
  }

}
