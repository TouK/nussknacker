package pl.touk.nussknacker.engine.util.test

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.util.NotNothing
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.graph.node.CompilableNodeData

object TestNodeCompiler

trait TestNodeCompiler {

  def compileNode[CompiledObject: NotNothing](
      nodeData: CompilableNodeData,
      variableTypes: Map[String, TypingResult] = Map.empty,
      branchVariableTypes: Option[Map[String, Map[String, TypingResult]]] = None,
      outgoingEdges: List[OutgoingEdge] = List.empty
  ): NodeCompiler.NodeCompilationResult[CompiledObject]

}
