package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{DualEditor, RawEditor, SimpleEditor}

object EditorExtractor {

  def extract(param: Parameter): Option[ParameterEditor] = {
    val dualEditorAnnotation: DualEditor = param.getAnnotation(classOf[DualEditor])
    val simpleEditorAnnotation = param.getAnnotation(classOf[SimpleEditor])
    val rawEditorAnnotation = param.getAnnotation(classOf[RawEditor])

    (dualEditorAnnotation, simpleEditorAnnotation, rawEditorAnnotation) match {
      case (dualEditorAnnotation: DualEditor, null, null) =>
        Some(DualParameterEditor(
          SimpleParameterEditor(
            dualEditorAnnotation.simpleEditor().`type`(),
            dualEditorAnnotation.simpleEditor().possibleValues().map(value => FixedExpressionValue(value.expression(), value.label())).toList
          ),
          dualEditorAnnotation.defaultMode()
        ))
      case (null, simpleEditorAnnotation: SimpleEditor, null) =>
        Some(SimpleParameterEditor(
          simpleEditorAnnotation.`type`(),
          simpleEditorAnnotation.possibleValues().map(value => FixedExpressionValue(value.expression(), value.label())).toList)
        )
      case (null, null, rawEditorAnnotation: RawEditor) => Some(RawParameterEditor)
      case _ => None
    }
  }
}
