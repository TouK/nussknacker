package pl.touk.nussknacker.engine.compile.nodevalidation

import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.definition.{Parameter => ParameterDef}
import pl.touk.nussknacker.engine.api.expression.{TypedExpression, TypedExpressionMap}
import pl.touk.nussknacker.engine.api.{Context, MetaData}
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.{Parameter, TypedParameter}
import pl.touk.nussknacker.engine.definition.ExpressionLazyParameter
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.util.Implicits._

import scala.concurrent.Await
import scala.concurrent.duration._

class ParameterEvaluator(expressionEvaluator: ExpressionEvaluator) {

  private val contextToUse: Context = Context("objectCreate")

  def prepareParameter(typedParameter: TypedParameter, definition: ParameterDef)(implicit processMetaData: MetaData, nodeId: NodeId): AnyRef = {
    if (definition.isLazyParameter) {
      prepareLazyParameter(typedParameter, definition)
    } else {
      evaluateParam(typedParameter, definition)
    }
  }

  private def prepareLazyParameter[T](param: TypedParameter, definition: ParameterDef)(implicit nodeId: NodeId): AnyRef = {
    param.typedValue match {
      case e:TypedExpression if !definition.branchParam =>
        prepareLazyParameterExpression(definition, e)
      case TypedExpressionMap(valueByKey) if definition.branchParam =>
        valueByKey.mapValuesNow(prepareLazyParameterExpression(definition, _))
    }
  }

  private def evaluateParam[T](param: TypedParameter, definition: ParameterDef)
                              (implicit processMetaData: MetaData, nodeId: NodeId) = {

    param.typedValue match {
      case e:TypedExpression if !definition.branchParam =>
        evaluateSync(Parameter(e, definition))
      case TypedExpressionMap(valueByKey) if definition.branchParam =>
        valueByKey.mapValuesNow(exp => evaluateSync(Parameter(exp, definition)))
    }
  }

  private def prepareLazyParameterExpression[T](definition: ParameterDef, exprValue: TypedExpression)(implicit nodeId: NodeId): ExpressionLazyParameter[Nothing] = {
    ExpressionLazyParameter(nodeId, definition,
      graph.expression.Expression(exprValue.expression.language, exprValue.expression.original), exprValue.returnType)
  }

  private def evaluateSync(param: Parameter)(implicit processMetaData: MetaData, nodeId: NodeId): AnyRef = {
    expressionEvaluator.evaluateParameter(param, contextToUse).value
  }

}
