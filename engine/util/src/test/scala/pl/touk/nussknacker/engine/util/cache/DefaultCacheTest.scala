package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache.Ticker
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration.Duration.Inf
import scala.concurrent.duration.{DAYS, Duration, FiniteDuration, MINUTES}

class DefaultCacheTest extends FlatSpec with Matchers {

  private var currentTime = Duration.fromNanos(System.nanoTime())
  private val ticker = new Ticker {
    override def read(): Long = currentTime.toNanos
  }

  it should "not expire any value when no expiry times configured" in {
    val cache = new DefaultCache[String, String](
      cacheConfig = CacheConfig(),
      ticker)

    cache.getOrCreate("key")("value")

    currentTime += FiniteDuration(1, DAYS)
    cache.getOrCreate("key")("newValue") shouldEqual "value"
  }

  it should "expire a value when the after write expiration is set, but not prolong it after reading" in {
    val cache = new DefaultCache[String, String](
      cacheConfig = CacheConfig(expireAfterWrite = Some(FiniteDuration(3, MINUTES))),
      ticker)

    cache.getOrCreate("key")("value")

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key")("ignoredValue")

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key")("newValue") shouldEqual "newValue"
  }

  it should "prolong a value when the after read expiration is set" in {
    val cache = new DefaultCache[String, String](
      cacheConfig = CacheConfig(
        expireAfterWrite = Some(FiniteDuration(3, MINUTES)),
        expireAfterAccess = Some(FiniteDuration(3, MINUTES))
      ),
      ticker)

    cache.getOrCreate("key")("value")

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key")("ignoredValue")

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key")("newValue") shouldEqual "value"
  }

  it should "allow setting expiration time depending on a value" in {
    case class Value(sub: String, exp: Duration)
    val cache = new DefaultCache[String, Value](
      cacheConfig = CacheConfig(
        expiry = new ExpiryConfig[String, Value] {
          override def expireAfterWriteFn(key: String, value: Value): Duration = value.exp
        }),
      ticker)

    cache.getOrCreate("key1")(Value("value1", FiniteDuration(1, MINUTES)))
    cache.getOrCreate("key2")(Value("value2", FiniteDuration(5, MINUTES)))

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key1")(Value("newValue", Inf)) should have ('sub ("newValue"))
    cache.getOrCreate("key2")(Value("newValue", Inf)) should have ('sub ("value2"))

    currentTime += FiniteDuration(4, MINUTES)
    cache.getOrCreate("key2")(Value("newValue", Inf)) should have ('sub ("newValue"))
  }
}
