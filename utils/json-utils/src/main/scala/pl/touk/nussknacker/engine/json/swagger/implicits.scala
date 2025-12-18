package pl.touk.nussknacker.engine.json.swagger

import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor}

object implicits {

  implicit class RichSwaggerTyped(st: SwaggerTyped) {

    def editorList: List[ParameterEditor] =
      st match {
        // TODO: FixedValuesParameterEditor for other types e.g. numbers
        case SwaggerEnum(values) if values.forall(v => v.isInstanceOf[String]) =>
          List(
            FixedValuesParameterEditor(
              values.map(value => FixedExpressionValue(s"'$value'", value.asInstanceOf[String]))
            )
          )
        // Default for others
        case _ => Nil
      }

  }

}
