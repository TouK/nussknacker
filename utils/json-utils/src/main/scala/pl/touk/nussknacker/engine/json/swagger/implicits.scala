package pl.touk.nussknacker.engine.json.swagger

import cats.implicits.catsSyntaxOptionId
import pl.touk.nussknacker.engine.api.definition.{
  BoolParameterEditor,
  DateParameterEditor,
  DateTimeParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  ParameterEditor,
  ParameterEditors,
  SpelParameterEditor,
  SpelTemplateParameterEditor,
  TimeParameterEditor
}

object implicits {

  implicit class RichSwaggerTyped(st: SwaggerTyped) {

    def editorList: Option[ParameterEditors] =
      st match {
        case SwaggerString =>
          ParameterEditors(
            SpelTemplateParameterEditor,
            SpelParameterEditor
          ).some
        case SwaggerBool =>
          ParameterEditors(
            BoolParameterEditor,
            SpelParameterEditor
          ).some
        case SwaggerTime =>
          ParameterEditors(
            TimeParameterEditor,
            SpelParameterEditor
          ).some
        case SwaggerDate =>
          ParameterEditors(
            DateParameterEditor,
            SpelParameterEditor
          ).some
        case SwaggerDateTime =>
          ParameterEditors(
            DateTimeParameterEditor,
            SpelParameterEditor
          ).some
        // TODO: FixedValuesParameterEditor for other types e.g. numbers
        case SwaggerEnum(values) if values.forall(v => v.isInstanceOf[String]) =>
          ParameterEditors(
            FixedValuesParameterEditor(
              values.map(value => FixedExpressionValue(s"'$value'", value.asInstanceOf[String]))
            )
          ).some
        case _ => None
      }

  }

}
