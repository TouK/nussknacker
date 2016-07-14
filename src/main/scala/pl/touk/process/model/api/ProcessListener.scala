package pl.touk.process.model.api

import pl.touk.process.model.graph.node.Node

trait ProcessListener {

  def nodeEntered(ctx: Ctx, node: Node): Unit

  def serviceInvoked(id: String, result: Any): Unit

}
