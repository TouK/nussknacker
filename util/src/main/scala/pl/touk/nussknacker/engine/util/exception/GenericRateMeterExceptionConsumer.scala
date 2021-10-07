package pl.touk.nussknacker.engine.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.{EspExceptionConsumer, EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

trait GenericRateMeterExceptionConsumer extends EspExceptionConsumer {

  def underlying: EspExceptionConsumer

  def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter

  private var allErrorsMeter: RateMeter = _

  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, RateMeter]()

  def open(): Unit = {
    allErrorsMeter = instantRateMeter(Map(), NonEmptyList.of("error", "instantRate"))
  }

  override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {
    try {
      underlying.consume(exceptionInfo)
    } finally {
      allErrorsMeter.mark()
      getMeterForNode(exceptionInfo.nodeId.getOrElse("unknown")).mark()
    }
  }

  private def getMeterForNode(nodeId: String): RateMeter =
    nodeErrorsMeterMap.getOrElseUpdate(nodeId, instantRateMeter(Map("nodeId" -> nodeId),  NonEmptyList.of("error", "instantRateByNode")))

}
