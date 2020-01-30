package pl.touk.nussknacker.ui.definition.editor

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition.{Parameter, ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.process.ParameterConfig

object ParameterEditorExtractorChain extends LazyLogging {

  def apply(parameterConfig: ParameterConfig): ParameterEditorExtractorChain = {
    val strategies = Seq(
      new ParameterConfigEditorExtractor(parameterConfig),
      ParameterEditorExtractor,
      ParameterTypeEditorExtractor
    )
    logger.debug("Building ParameterEditorExtractorChain with strategies: {}", strategies)
    new ParameterEditorExtractorChain(strategies)
  }
}

class ParameterEditorExtractorChain(elements: Iterable[ParameterEditorExtractorStrategy]) {

  def evaluateEditor(param: Parameter): ParameterEditor = {
    val value = elements.view
      .flatMap(_.evaluateParameterEditor(param))
    value
      .headOption
      .getOrElse(RawParameterEditor)
  }
}
