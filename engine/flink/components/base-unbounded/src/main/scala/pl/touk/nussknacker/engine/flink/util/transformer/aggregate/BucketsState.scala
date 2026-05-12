package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.state.{MapState, ValueState}

import scala.jdk.CollectionConverters._

/**
 * Encapsulates bucket state management for MapState-based aggregators.
 *
 * Maintains a separate ValueState with bucket keys (timestamps) to avoid
 * expensive prefix scans over RocksDB MapState for key-only operations
 * like range checks, max computation, and eviction decisions.
 */
class BucketsState(
    private val mapState: MapState[java.lang.Long, AnyRef],
    private val keysState: ValueState[java.util.List[java.lang.Long]],
    private val latestEvictionTimeState: ValueState[java.lang.Long]
) {

  def keys: java.util.List[java.lang.Long] = keysState.value() match {
    case null => new java.util.ArrayList[java.lang.Long]()
    case v    => v
  }

  def get(key: java.lang.Long): AnyRef = mapState.get(key)

  def put(key: java.lang.Long, value: AnyRef): Unit = {
    mapState.put(key, value)
    val currentKeys = keys
    if (!currentKeys.contains(key)) {
      currentKeys.add(key)
      keysState.update(currentKeys)
    }
  }

  def removeOlderThan(cutoff: Long): Unit = {
    val allKeys            = keys.asScala.toList
    val (toRemove, toKeep) = allKeys.partition(_.longValue() < cutoff)
    if (toRemove.nonEmpty) {
      toRemove.foreach(mapState.remove)
      keysState.update(new java.util.ArrayList(toKeep.asJava))
    }
  }

  def latestEvictionTime: java.lang.Long = latestEvictionTimeState.value()

  def updateLatestEvictionTime(time: java.lang.Long): Unit = latestEvictionTimeState.update(time)

  def clear(): Unit = {
    mapState.clear()
    keysState.update(null)
    latestEvictionTimeState.update(null)
  }

}
