package pl.touk.nussknacker.engine.compiledgraph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.MetaData

case class CompiledProcessParts(metaData: MetaData, sources: NonEmptyList[part.PotentiallyStartPart])