package pl.touk.nussknacker.ui.process.test

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.NodeTypingInfo

package object testcase {

  final case class NodeTyping(
      inputVariables: Map[String, TypingResult],
      outputVariables: Map[String, TypingResult],
  )

  object NodeTyping {
    val empty = NodeTyping(Map.empty, Map.empty)

    def apply(nodeTypingInfo: NodeTypingInfo): NodeTyping = {
      NodeTyping(
        nodeTypingInfo.inputValidationContext.localVariables,
        nodeTypingInfo.outputValidationContext.map(_.localVariables).getOrElse(Map.empty),
      )
    }

  }

}
