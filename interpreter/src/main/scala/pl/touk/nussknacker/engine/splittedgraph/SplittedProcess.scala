package pl.touk.nussknacker.engine.splittedgraph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.splittedgraph.part.SourcePart

case class SplittedProcess(metaData: MetaData, sources: NonEmptyList[SourcePart])
