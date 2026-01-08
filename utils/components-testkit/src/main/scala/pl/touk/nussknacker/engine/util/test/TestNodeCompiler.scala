package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.CompilesTo
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.graph.node.CompilableNodeData

object TestNodeCompiler

trait TestNodeCompiler {

  def compileNode[NodeData <: CompilableNodeData](
      nodeData: NodeData,
      variableTypes: Map[String, TypingResult] = Map.empty,
      branchVariableTypes: Option[Map[String, Map[String, TypingResult]]] = None,
      outgoingEdges: List[OutgoingEdge] = List.empty
  )(implicit compilesTo: CompilesTo[NodeData]): NodeCompiler.NodeCompilationResult[compilesTo.ReturnType]

}
