package pl.touk.nussknacker.engine.flink.util.orderedmap

import java.{util => jul}

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.MapTypeInfo
import org.apache.flink.api.scala._

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

  implicit object SortedMapFlinkRangeMap extends FlinkRangeMap[SortedMap] {

    override def typeInformation[K: TypeInformation, V: TypeInformation]: TypeInformation[SortedMap[K, V]] =
      implicitly[TypeInformation[SortedMap[K, V]]]

    override def empty[K: Ordering, V]: SortedMap[K, V] = SortedMap.empty[K, V]

    override def from[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = map.from(key)

    override def to[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = map.to(key)

    override def filterKeys[K, V](map: SortedMap[K, V], p: K => Boolean): SortedMap[K, V] = map.filterKeys(p)

    override def updated[K, V](map: SortedMap[K, V], key: K, value: V): SortedMap[K, V] = map.updated(key, value)

    override def fromRO[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = from(map, key)

    override def toRO[K: Ordering, V](map: SortedMap[K, V], key: K): SortedMap[K, V] = to(map, key)

    override def toScalaMapRO[K, V](map: SortedMap[K, V]): collection.Map[K, V] = map

  }

  implicit object JavaHashMapFlinkRangeMap extends FlinkRangeMap[jul.Map] {

    import scala.collection.JavaConverters._

    override def typeInformation[K: TypeInformation, V: TypeInformation]: TypeInformation[jul.Map[K, V]] =
      new MapTypeInfo[K, V](implicitly[TypeInformation[K]], implicitly[TypeInformation[V]])

    override def empty[K: Ordering, V]: jul.Map[K, V] = new jul.HashMap[K, V]()

    // mutating methods

    override def from[K: Ordering, V](map: jul.Map[K, V], key: K): jul.Map[K, V] = filterKeys(map, _ >= key)

    override def to[K: Ordering, V](map: jul.Map[K, V], key: K): jul.Map[K, V] = filterKeys(map, _ <= key)

    override def filterKeys[K, V](map: jul.Map[K, V], p: K => Boolean): jul.Map[K, V] = {
      map.asScala.retain((k, _) => p(k))
      map
    }

    override def updated[K, V](map: jul.Map[K, V], key: K, value: V): jul.Map[K, V] = {
      map.put(key, value)
      map
    }

    // read-only methods

    override def fromRO[K: Ordering, V](map: jul.Map[K, V], key: K): jul.Map[K, V] = transformAsScalaMapRO(map, _.filterKeys(_ >= key))

    override def toRO[K: Ordering, V](map: jul.Map[K, V], key: K): jul.Map[K, V] = transformAsScalaMapRO(map, _.filterKeys(_ <= key))

    private def transformAsScalaMapRO[K, V](map: jul.Map[K, V],
                                            transform: collection.Map[K, V] => collection.Map[K, V]): jul.Map[K, V] =
      transform(toScalaMapRO(map)).asJava

    override def toScalaMapRO[K, V](map: jul.Map[K, V]): collection.Map[K, V] = map.asScala

  }

  implicit class FlinkRangeMapOps[MapT[KK, VV]: FlinkRangeMap, K: Ordering, V](map: MapT[K, V]) {

    // potentially mutating methods

    def from(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].from(map, key)

    def to(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].to(map, key)

    def filterKeys(p: K => Boolean): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].filterKeys(map, p)

    def updated(key: K, value: V): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].updated(map, key, value)

    // read-only methods

    def fromRO(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].fromRO(map, key)

    def toRO(key: K): MapT[K, V] = implicitly[FlinkRangeMap[MapT]].toRO(map, key)

    def toScalaMapRO: collection.Map[K, V] = implicitly[FlinkRangeMap[MapT]].toScalaMapRO(map)

  }

}
