package pl.touk.nussknacker.engine.util.cache

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.util.cache.DefaultCache.defaultMaximumSize

class DefaultCacheSpec extends FlatSpec with Matchers {

  import scala.concurrent.duration._

  it should "properly return data from cache" in {
    val cache = new DefaultCache[String](defaultMaximumSize, None, registerStats = true)
    val value = "{'name': 'DefaultCacheSpec'}"
    val times = 3

    1 to times foreach {
      _ => cache.getOrCreate("json-v-1", None) {value}
    }

    cache.hitCount shouldBe times - 1
  }

  it should "properly return data from cache with sets expireAfterAccess" in {
    val cache = new DefaultCache[String](defaultMaximumSize, Some(2.nanoseconds), registerStats = true)
    val value = "{'name': 'DefaultCacheSpec'}"
    val times = 3

    1 to times foreach {
      _ => cache.getOrCreate("json-v-1", None) {value}
      Thread.sleep(2.nanoseconds.toMicros)
    }

    cache.missCount shouldBe times
  }

  it should "properly return data from cache with ttl" in {
    val cache = new DefaultCache[String](defaultMaximumSize, None, registerStats = true)
    val value = "{'name': 'DefaultCacheSpec'}"
    val times = 3

    1 to times foreach {
      _ => cache.getOrCreate("json-v-1", Some(1.nanoseconds)) {value}
        Thread.sleep(1.microseconds.toMicros)
    }

    cache.missCount shouldBe times
  }

  it should "throw exception when ttl is higher then expireAfterAccess" in {
    val cache = new DefaultCache[String](defaultMaximumSize, Some(1.nanoseconds), registerStats = true)
    val value = "{'name': 'DefaultCacheSpec'}"

    assertThrows[IllegalArgumentException] {
      cache.getOrCreate("json-v-1", Some(2.nanoseconds)) {value}
    }
  }
}
