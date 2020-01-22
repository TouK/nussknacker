package pl.touk.nussknacker.restmodel.displayedgraph

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.node.NodeData

case class UiNodeContext(nodeData: NodeData, validationContext: Map[String, TypingResult], processingType: ProcessingType)
