package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{
  CustomNode,
  Enricher,
  Filter,
  FragmentInput,
  Join,
  Processor,
  Sink,
  Source,
  Switch,
  Variable,
  VariableBuilder,
  WithParameters
}
import pl.touk.nussknacker.engine.graph.variable.Field

class ExpressionMigration(
    private val mapping: PartialFunction[Expression, Expression]
) {

  // comment
  def migrateNode: PartialFunction[node.NodeData, node.NodeData] = {
    case n: CustomNode if shouldApplyMappingToParams(n) =>
      n.copy(parameters = n.parameters.map(applyMappingOrSelf))
    case n: Enricher if shouldApplyMappingToParams(n) =>
      n.copy(service = n.service.copy(parameters = n.service.parameters.map(applyMappingOrSelf)))
    case n: Filter if shouldApplyMappingToExpression(n.expression) =>
      n.copy(expression = applyMappingOrSelf(n.expression))
    case node: FragmentInput if shouldApplyMappingToParams(node.ref.parameters) =>
      node.copy(ref = node.ref.copy(parameters = node.ref.parameters.map(applyMappingOrSelf)))
    case n: Join if shouldApplyMappingToParams(n) || shouldApplyMappingToBranchParams(n.branchParameters) =>
      n.copy(
        parameters = n.parameters.map(applyMappingOrSelf),
        branchParameters = n.branchParameters.map(p => p.copy(parameters = p.parameters.map(applyMappingOrSelf)))
      )
    case n: Processor if shouldApplyMappingToParams(n) =>
      n.copy(service = n.service.copy(parameters = n.service.parameters.map(applyMappingOrSelf)))
    case n: Sink if shouldApplyMappingToParams(n) =>
      n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(applyMappingOrSelf)))
    case n: Switch if n.expression.exists(shouldApplyMappingToExpression) =>
      n.copy(expression = n.expression.map(applyMappingOrSelf))
    case n: Source if shouldApplyMappingToParams(n) =>
      n.copy(ref = n.ref.copy(parameters = n.ref.parameters.map(applyMappingOrSelf)))
    case n: Variable if shouldApplyMappingToExpression(n.value) =>
      n.copy(value = applyMappingOrSelf(n.value))
    case n: VariableBuilder if shouldApplyMappingToFields(n.fields) =>
      n.copy(fields = n.fields.map(field => field.copy(expression = applyMappingOrSelf(field.expression))))
  }

  private def shouldApplyMappingToParams(withParameters: WithParameters): Boolean =
    shouldApplyMappingToParams(withParameters.parameters)

  private def shouldApplyMappingToParams(parameters: List[Parameter]): Boolean =
    parameters.exists(param => shouldApplyMappingToExpression(param.expression))

  private def shouldApplyMappingToBranchParams(branchParameters: List[BranchParameters]): Boolean =
    branchParameters.exists(_.parameters.exists(param => shouldApplyMappingToExpression(param.expression)))

  private def shouldApplyMappingToFields(fields: List[Field]): Boolean =
    fields.exists(field => shouldApplyMappingToExpression(field.expression))

  private def shouldApplyMappingToExpression(expression: Expression): Boolean =
    mapping.isDefinedAt(expression)

  private def applyMappingOrSelf(parameter: Parameter): Parameter =
    parameter.copy(expression = applyMappingOrSelf(parameter.expression))

  private def applyMappingOrSelf(expression: Expression): Expression =
    mapping.applyOrElse(expression, identity[Expression])

}
