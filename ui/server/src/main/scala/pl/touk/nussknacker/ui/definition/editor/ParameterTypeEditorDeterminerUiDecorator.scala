package pl.touk.nussknacker.ui.definition.editor

import pl.touk.nussknacker.engine.api.definition.{CronParameterEditor, DualParameterEditor, ParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.{ParameterEditorDeterminer, ParameterTypeEditorDeterminer}

/**
 * We want to keep package nusskancker-interpreter with as less dependencies as possible - that's why we handle
 * com.cronutils.model.Cron editor here
 */
protected class ParameterTypeEditorDeterminerUiDecorator(determiner: ParameterTypeEditorDeterminer) extends ParameterEditorDeterminer {

  override def determine(): Option[ParameterEditor] = {
    determiner.determine() match {
      case Some(editor) => Some(editor)
      case None if determiner.typ == Typed[com.cronutils.model.Cron] => Some(DualParameterEditor(
        simpleEditor = CronParameterEditor,
        defaultMode = DualEditorMode.SIMPLE
      ))
      case _ => Some(RawParameterEditor)
    }
  }
}
