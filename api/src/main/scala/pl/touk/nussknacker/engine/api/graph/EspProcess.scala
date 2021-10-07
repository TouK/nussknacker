package pl.touk.nussknacker.engine.api.graph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.api.graph.node.SourceNode

object EspProcess {

  def apply(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef,
                        root: SourceNode): EspProcess = EspProcess(metaData, exceptionHandlerRef, NonEmptyList.of(root))
}

case class EspProcess(metaData: MetaData, exceptionHandlerRef: ExceptionHandlerRef,
                      roots: NonEmptyList[SourceNode]) {
  def id: String = metaData.id
}

