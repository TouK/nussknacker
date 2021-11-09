package pl.touk.nussknacker.engine.graph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node.SourceNode

object EspProcess {

  def apply(metaData: MetaData, root: SourceNode): EspProcess = EspProcess(metaData, NonEmptyList.of(root))
}

case class EspProcess(metaData: MetaData, roots: NonEmptyList[SourceNode]) {
  def id: String = metaData.id
}

