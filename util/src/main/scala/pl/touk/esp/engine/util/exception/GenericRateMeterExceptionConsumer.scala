package pl.touk.esp.engine.util.exception

import pl.touk.esp.engine.api.exception.{EspExceptionConsumer, EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.util.metrics.GenericInstantRateMeter

trait GenericRateMeterExceptionConsumer extends EspExceptionConsumer {

  def underlying: EspExceptionConsumer

  def instantRateMeter(name: String*): GenericInstantRateMeter

  private var allErrorsMeter: GenericInstantRateMeter = _

  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, GenericInstantRateMeter]()

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
