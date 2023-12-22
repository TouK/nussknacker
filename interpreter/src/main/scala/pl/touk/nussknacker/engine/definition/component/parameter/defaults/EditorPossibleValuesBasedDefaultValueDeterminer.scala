package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData
import pl.touk.nussknacker.engine.api.definition.TabularTypedDataEditor.TabularTypedData.Column
import pl.touk.nussknacker.engine.api.definition.{
  DualParameterEditor,
  FixedValuesParameterEditor,
  TabularTypedDataEditor
}
import pl.touk.nussknacker.engine.graph.expression.Expression

protected object EditorPossibleValuesBasedDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.determinedEditor
      .flatMap {
        case FixedValuesParameterEditor(firstValue :: _) => Some(Expression.spel(firstValue.expression))
        // it is better to see error that field is not filled instead of strange default value like '' for String
        case FixedValuesParameterEditor(Nil) => Some(Expression.spel(""))
        case DualParameterEditor(FixedValuesParameterEditor(firstValue :: _), _) =>
          Some(Expression.spel(firstValue.expression))
        case TabularTypedDataEditor =>
          val expressionString = TabularTypedData
            .encoder(
              TabularTypedData
                .create(
                  Vector(
                    Column("some name", classOf[java.lang.Double]),
                    Column("B", classOf[java.lang.String]),
                    Column("C", classOf[java.lang.String])
                  ),
                  Vector(
                    Vector(null, null, "test"),
                    Vector(1.0, "foo", "bar"),
                    Vector(null, null, "xxx")
                  )
                )
                .get
            )
            .noSpaces
          // val expressionString = TabularTypedData.encoder(TabularTypedData.empty).noSpaces
          Some(Expression.tabularDataDefinition(expressionString))
        case _ => None
      }
  }

}
