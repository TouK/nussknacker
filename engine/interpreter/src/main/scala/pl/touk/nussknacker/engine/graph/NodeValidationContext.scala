package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class NodeValidationContext(nodeData: NodeData, context: Map[String, TypingResult])
