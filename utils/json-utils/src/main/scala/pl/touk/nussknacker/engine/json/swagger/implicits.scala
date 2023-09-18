package pl.touk.nussknacker.engine.json.swagger

import pl.touk.nussknacker.engine.api.definition.{BoolParameterEditor, DateParameterEditor, DateTimeParameterEditor, DualParameterEditor, FixedValuesParameterEditor, ParameterEditor, StringParameterEditor, TimeParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.graph.expression.FixedExpressionValue

object implicits {

  implicit class RichSwaggerTyped(st: SwaggerTyped) {
    def editorOpt: Option[ParameterEditor] =
      st match {
        case SwaggerString => Some(
          DualParameterEditor(
            simpleEditor = StringParameterEditor,
            defaultMode = DualEditorMode.RAW
          )
        )
        case SwaggerBool => Some(
          DualParameterEditor(
            simpleEditor = BoolParameterEditor,
            defaultMode = DualEditorMode.SIMPLE
          )
        )
        case SwaggerTime => Some(
          DualParameterEditor(
            simpleEditor = TimeParameterEditor,
            defaultMode = DualEditorMode.SIMPLE
          )
        )
        case SwaggerDate => Some(
          DualParameterEditor(
            simpleEditor = DateParameterEditor,
            defaultMode = DualEditorMode.SIMPLE
          )
        )
        case SwaggerDateTime => Some(
          DualParameterEditor(
            simpleEditor = DateTimeParameterEditor,
            defaultMode = DualEditorMode.SIMPLE
          )
        )
        // TODO: FixedValuesParameterEditor for other types e.g. numbers
        case SwaggerEnum(values) if values.forall(v => v.isInstanceOf[String]) => Some(
          FixedValuesParameterEditor(values.map(value => FixedExpressionValue(s"'$value'", value.asInstanceOf[String])))
        )
        case _ => None
      }
  }
}