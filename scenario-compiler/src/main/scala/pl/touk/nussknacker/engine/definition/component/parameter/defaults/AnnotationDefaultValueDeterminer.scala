package pl.touk.nussknacker.engine.definition.component.parameter.defaults

import pl.touk.nussknacker.engine.api.{DefaultValue, ExpressionLanguage}
import pl.touk.nussknacker.engine.graph.expression.Expression

object AnnotationDefaultValueDeterminer extends ParameterDefaultValueDeterminer {

  override def determineParameterDefaultValue(parameters: DefaultValueDeterminerParameters): Option[Expression] = {
    parameters.parameterData.getAnnotation[DefaultValue].map(annotationToExpression)
  }

  private def annotationToExpression(defaultValue: DefaultValue): Expression = defaultValue.language() match {
    case ExpressionLanguage.SPEL                    => Expression.spel(defaultValue.value())
    case ExpressionLanguage.SPEL_TEMPLATE           => Expression.spelTemplate(defaultValue.value())
    case ExpressionLanguage.DICT_KEY_WITH_LABEL     => Expression.dictKeyWithLabel(defaultValue.value(), None)
    case ExpressionLanguage.TABULAR_DATA_DEFINITION => Expression.tabularDataDefinition(defaultValue.value())
    case ExpressionLanguage.JSON                    => Expression.json(defaultValue.value())
    case ExpressionLanguage.JSON_TEMPLATE           => Expression.jsonTemplate(defaultValue.value())
  }

}
