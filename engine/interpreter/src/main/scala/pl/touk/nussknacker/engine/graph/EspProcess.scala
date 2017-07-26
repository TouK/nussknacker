package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SourceNode

case class EspProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef, root: SourceNode) {
  def id = metaData.id
}

