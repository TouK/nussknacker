package pl.touk.nussknacker.http.enricher

import scala.jdk.CollectionConverters._

// TODO http: extract to utils outside of this subproject?
object MapExtensions {

  implicit class MapToHashMapExtension[K, V](map: Map[K, V]) {

    def toHashMap: java.util.HashMap[K, V] = {
      new java.util.HashMap(map.asJava)
    }

  }

}
