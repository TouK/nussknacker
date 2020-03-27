package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.process.ParameterConfig
import pl.touk.nussknacker.engine.definition.{ParameterEditorDeterminer, ParameterTypeEditorDeterminer}

object ParameterEditorDeterminerChain {

  def apply(param: Parameter, parameterConfig: ParameterConfig): ParameterEditorDeterminerChain = {
    val strategies = Seq(
      new ParameterConfigEditorDeterminer(parameterConfig),
      new ParameterBasedEditorDeterminer(param),
      new ParameterTypeEditorDeterminerUiDecorator(new ParameterTypeEditorDeterminer(param.runtimeClass))
    )
    new ParameterEditorDeterminerChain(strategies)
  }
}

class ParameterEditorDeterminerChain(elements: Iterable[ParameterEditorDeterminer]) {

  def determineEditor(): ParameterEditor = {
    val value = elements.view
      .flatMap(_.determine())
    value
      .headOption
      .getOrElse(RawParameterEditor)
  }
}
