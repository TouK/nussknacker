package pl.touk.nussknacker.engine.json.swagger

import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, ParameterEditor}

object implicits {

  implicit class RichSwaggerTyped(st: SwaggerTyped) {
    def editorOpt: Option[ParameterEditor] =
      st match {
        //todo: FixedValuesParameterEditor for other types e.g. numbers
        case SwaggerEnum(values) if values.forall(v => v.isInstanceOf[String]) => Some(
          FixedValuesParameterEditor(values.map(value => FixedExpressionValue(s"'$value'", value.asInstanceOf[String])))
        )
        case _ => None
      }
  }

}
