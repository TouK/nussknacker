package pl.touk.nussknacker.engine.compiledgraph

import cats.data.NonEmptyList

case class CompiledProcessParts(sources: NonEmptyList[part.PotentiallyStartPart])
