package pl.touk.nussknacker.ui.definition.editor

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

object ParameterEditorDeterminerChain extends LazyLogging {

  def apply(parameterConfig: ParameterConfig): ParameterEditorDeterminerChain = {
    val strategies = Seq(
      new ParameterConfigEditorDeterminer(parameterConfig),
      ParameterBasedEditorDeterminer,
      ParameterTypeEditorDeterminer
    )
    logger.debug("Building ParameterEditorDeterminerChain with strategies: {}", strategies)
    new ParameterEditorDeterminerChain(strategies)
  }
}

class ParameterEditorDeterminerChain(elements: Iterable[ParameterEditorDeterminer]) {

  def determineEditor(param: Parameter): ParameterEditor = {
    val value = elements.view
      .flatMap(_.determineParameterEditor(param))
    value
      .headOption
      .getOrElse(RawParameterEditor)
  }
}
