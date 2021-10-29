package pl.touk.nussknacker.engine.util.exception

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.util.metrics.RateMeter

trait ExceptionRateMeter {

  private lazy val allErrorsMeter: RateMeter = instantRateMeter(Map(), NonEmptyList.of("error", "instantRate"))

  private val nodeErrorsMeterMap = collection.concurrent.TrieMap[String, RateMeter]()

  def instantRateMeter(tags: Map[String, String], name: NonEmptyList[String]): RateMeter

  protected def markException(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {
    allErrorsMeter.mark()
    getMeterForNode(exceptionInfo.nodeId.getOrElse("unknown")).mark()
  }

  private def getMeterForNode(nodeId: String): RateMeter =
    nodeErrorsMeterMap.getOrElseUpdate(nodeId, instantRateMeter(Map("nodeId" -> nodeId), NonEmptyList.of("error", "instantRateByNode")))

}
