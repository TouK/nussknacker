package pl.touk.nussknacker.engine.definition

import java.lang.reflect.Parameter

import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.editor.{DualEditor, RawEditor, SimpleEditor}

object EditorExtractor {

  def extract(param: Parameter): Option[ParameterEditor] = {
    val dualEditorAnnotation = param.getAnnotation(classOf[DualEditor])
    val simpleEditorAnnotation = param.getAnnotation(classOf[SimpleEditor])
    val rawEditorAnnotation = param.getAnnotation(classOf[RawEditor])
    if (dualEditorAnnotation != null)
      Some(DualParameterEditor(
        SimpleParameterEditor(
          dualEditorAnnotation.simpleEditor().`type`(),
          dualEditorAnnotation.simpleEditor().possibleValues().map(value => FixedExpressionValue(value.expression(), value.label())).toList
        ),
        dualEditorAnnotation.defaultMode()
      ))
    else if (simpleEditorAnnotation != null)
      Some(SimpleParameterEditor(
        simpleEditorAnnotation.`type`(),
        simpleEditorAnnotation.possibleValues().map(value => FixedExpressionValue(value.expression(), value.label())).toList)
      )
    else if (rawEditorAnnotation != null) Some(RawParameterEditor)
    else None
  }
}
