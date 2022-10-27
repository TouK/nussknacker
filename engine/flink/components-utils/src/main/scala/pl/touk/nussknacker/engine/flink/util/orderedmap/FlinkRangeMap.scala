package pl.touk.nussknacker.engine.flink.util.orderedmap

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}

import java.{util => jul}
import scala.Ordering.Implicits._
import scala.collection.immutable.SortedMap
import scala.language.higherKinds

/**
 * This is type class handling operations on maps: updating and range filtering.
 * For optimization reasons it contains two categories of methods - potentially mutating and read-only.
 * After invocation of read-only methods, you shouldn't invoke methods that potentially mutate object because
 * Exception can be thrown.
 */
trait FlinkRangeMap[MapT[K,V]] extends Serializable {

  def typeInformation[K: TypeInformation, V: TypeInformation]: TypeInformation[MapT[K, V]]

  def empty[K: Ordering, V]: MapT[K, V]

  // potentially mutating methods

  def from[K: Ordering, V](map: MapT[K, V], key: K): MapT[K, V]

  def to[K: Ordering, V](map: MapT[K, V], key: K): MapT[K, V]

  def filterKeys[K, V](map: MapT[K, V], p: K => Boolean): MapT[K, V]

  def updated[K, V](map: MapT[K, V], key: K, value: V): MapT[K, V]

  // read-only methods

  def fromRO[K: Ordering, V](map: MapT[K, V], key: K): MapT[K, V]

  def toRO[K: Ordering, V](map: MapT[K, V], key: K): MapT[K, V]

  def toScalaMapRO[K, V](map: MapT[K, V]): collection.Map[K, V]

}

object FlinkRangeMap {

  /**
   * This implementation is based on scala's immutable SortedMap (TreeMap).
   * It has good O(lgN) characteristics for range filtering which can be important for some usages.
   * In the other hand it uses Kryo for serialization which is inefficient
   */
  implicit object SortedMapFlinkRangeMap extends FlinkRangeMap[SortedMap] {

    override def typeInformation[K: TypeInformation, V: TypeInformation]: TypeInformation[SortedMap[K, V]] = {
      // TODO: Add better type information
      TypeInformation.of(new TypeHint[SortedMap[K, V]] {})
    }

    override def empty[K: Ordering, V]: SortedMap[K, V] = SortedMap.empty[K, V]

    override def from[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = map.from(key)

    override def to[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = map.to(key)

    override def filterKeys[K, V](map: SortedMap[K, V], p: K => Boolean): SortedMap[K, V] = map.filterKeys(p)

    override def updated[K, V](map: SortedMap[K, V], key: K, value: V): SortedMap[K, V] = map.updated(key, value)

    override def fromRO[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = from(map, key)

    override def toRO[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = to(map, key)

    override def toScalaMapRO[K, V](map: SortedMap[K, V]): collection.Map[K, V] = map

  }

  implicit class FlinkRangeMapOps[MapT[KK, VV]: FlinkRangeMap, K: Ordering, V](map: MapT[K, V]) {

    // potentially mutating methods

    def from(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].from(map, key)

    def to(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].to(map, key)

    def updated(key: K, value: V): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].updated(map, key, value)

    // read-only methods

    def fromRO(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].fromRO(map, key)

    def toRO(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].toRO(map, key)

    def toScalaMapRO: collection.Map[K, V] = implicitly[FlinkRangeMap[MapT]].toScalaMapRO(map)

  }

}
