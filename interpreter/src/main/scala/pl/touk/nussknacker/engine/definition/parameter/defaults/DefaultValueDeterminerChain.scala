package pl.touk.nussknacker.engine.definition.parameter.defaults

object DefaultValueDeterminerChain extends DefaultValueDeterminerChain(List(
  ConfigParameterDefaultValueDeterminer,
  AnnotationDefaultValueDeterminer,
  OptionalityBasedDefaultValueDeterminer,
  EditorPossibleValuesBasedDefaultValueDeterminer,
  TypeRelatedParameterValueDeterminer))

class DefaultValueDeterminerChain(elements: Iterable[ParameterDefaultValueDeterminer]) extends ParameterDefaultValueDeterminer {
  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[String] = {
    elements.view.flatMap(_.determineParameterDefaultValue(parameters)).headOption
  }
}
