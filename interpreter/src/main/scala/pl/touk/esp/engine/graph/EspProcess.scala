package pl.touk.esp.engine.graph

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node.SourceNode

case class EspProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef, root: SourceNode) {
  def id = metaData.id
}

