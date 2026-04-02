package pl.touk.nussknacker.engine.assembly

sealed trait MergeStrategy

object MergeStrategy {
  case object Concat              extends MergeStrategy
  case object Discard             extends MergeStrategy
  case object Deduplicate         extends MergeStrategy
  case object FilterDistinctLines extends MergeStrategy
  case object First               extends MergeStrategy
  case object Rename              extends MergeStrategy
}
