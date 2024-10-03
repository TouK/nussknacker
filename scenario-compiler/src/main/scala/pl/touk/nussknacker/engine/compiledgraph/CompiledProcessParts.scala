package pl.touk.nussknacker.engine.compiledgraph

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.MetaData

case class CompiledProcessParts(sources: NonEmptyList[part.PotentiallyStartPart])
