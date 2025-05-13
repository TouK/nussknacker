package pl.touk.nussknacker.engine.json.swagger

import pl.touk.nussknacker.engine.api.definition.{
  BoolParameterEditor,
  DateParameterEditor,
  DateTimeParameterEditor,
  FixedExpressionValue,
  FixedValuesParameterEditor,
  ParameterEditor,
  SpelParameterEditor,
  SpelTemplateParameterEditor,
  TimeParameterEditor
}

object implicits {

  implicit class RichSwaggerTyped(st: SwaggerTyped) {

    def editorList: List[ParameterEditor] =
      st match {
        case SwaggerString =>
          List(
            SpelTemplateParameterEditor,
            SpelParameterEditor
          )
        case SwaggerBool =>
          List(
            BoolParameterEditor,
            SpelParameterEditor
          )
        case SwaggerTime =>
          List(
            TimeParameterEditor,
            SpelParameterEditor
          )
        case SwaggerDate =>
          List(
            DateParameterEditor,
            SpelParameterEditor
          )
        case SwaggerDateTime =>
          List(
            DateTimeParameterEditor,
            SpelParameterEditor
          )
        // TODO: FixedValuesParameterEditor for other types e.g. numbers
        case SwaggerEnum(values) if values.forall(v => v.isInstanceOf[String]) =>
          List(
            FixedValuesParameterEditor(
              values.map(value => FixedExpressionValue(s"'$value'", value.asInstanceOf[String]))
            )
          )
        case _ => Nil
      }

  }

}
