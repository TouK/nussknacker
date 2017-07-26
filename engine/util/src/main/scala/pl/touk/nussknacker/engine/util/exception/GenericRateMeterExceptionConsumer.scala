package pl.touk.nussknacker.engine.util.exception

import pl.touk.nussknacker.engine.api.exception.{EspExceptionConsumer, EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

trait GenericRateMeterExceptionConsumer extends EspExceptionConsumer {

  def underlying: EspExceptionConsumer

  def instantRateMeter(name: String*): RateMeter

  private var allErrorsMeter: RateMeter = _

  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, RateMeter]()

  def open(): Unit = {
    allErrorsMeter = instantRateMeter("error", "instantRate")
  }

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]) = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      allErrorsMeter.mark()
      getMeterForNode(exceptionInfo.nodeId.getOrElse("unknown")).mark()
    }
  }

  private def getMeterForNode(nodeId: String) =
    nodeErrorsMeterMap.getOrElseUpdate(nodeId, instantRateMeter("error", nodeId, "instantRateByNode"))

}
