package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.ui.api.description.NodesApiEndpoints.Dtos.{
  NodeTestCase,
  NodeTestCases,
  NodeTestCasesValidationErrors,
  NodeTestCaseValidationErrors
}

class TestCaseValidator {

  def validateNodeTestCases(nodeData: NodeData, nodeTestCases: NodeTestCases): NodeTestCasesValidationErrors = {
    ???
  }

  private def validateSingleNodeTestCase(
      nodeData: NodeData,
      nodeTestCase: NodeTestCase
  ): Either[NodeTestCaseValidationErrors, Unit] = {
    ???
  }

}
