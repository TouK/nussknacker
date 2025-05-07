package pl.touk.nussknacker.engine.management.jobrunner.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.testmode.TestProcess._

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters._

private[livedata] class LiveDataCollectingListenerStorage {

  private type K = Context
  private type V = TestResults[Json]
  private val maxSize = 10

  private val map   = new ConcurrentHashMap[K, V]()
  private val order = new ConcurrentLinkedQueue[K]()

  def keys: List[K] =
    order.asScala.toList

  def values: List[V] =
    order.asScala.toList.flatMap(key => Option(map.get(key)))

  def updateResults(context: Context, action: TestResults[Json] => TestResults[Json]): Unit = {
    compute(
      context,
      (_: Context, maybeOldResults: Option[TestResults[Json]]) => {
        val oldResults = maybeOldResults match {
          case Some(results) =>
            results
          case None =>
            TestResults[Json](Map.empty, Map.empty, Map.empty, Map.empty, List.empty)
        }
        val newResults = action(oldResults)
        newResults
      }
    )
  }

  private def compute(key: K, remappingFunction: (K, Option[V]) => V): V = this.synchronized {
    val currentValue = Option(map.get(key))
    val newValue     = remappingFunction(key, currentValue)
    val isNewKey     = !map.containsKey(key)
    map.put(key, newValue)
    if (isNewKey) {
      order.add(key)
      while (order.size() > maxSize) {
        val oldest = order.poll()
        if (oldest != null) map.remove(oldest)
      }
    }
    newValue
  }

}
