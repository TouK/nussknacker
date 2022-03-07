package pl.touk.nussknacker.engine.util.metrics

case class InstantRateMeterWithCount(rateMeter: InstantRateMeter, counter: Counter) extends RateMeter {

  override def mark(): Unit = {
    rateMeter.mark()
    counter.update(1)
  }
}