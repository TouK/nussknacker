package pl.touk.nussknacker.engine.util.metrics

trait RateMeter {
  def mark(): Unit
}
