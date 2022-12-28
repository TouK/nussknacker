package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache.Ticker
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration.{DAYS, Deadline, FiniteDuration, HOURS, MINUTES}

class DefaultCacheTest extends AnyFlatSpec with Matchers with VeryPatientScalaFutures{

  private var currentTime = Deadline.now
  private val ticker = new Ticker {
    override def read(): Long = currentTime.time.toNanos
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

  it should "prolong a value when the after reading expiration is set and getOrCreate is used" in {
    val cache = new DefaultCache[String, String](
      cacheConfig = CacheConfig(expireAfterAccess = Some(FiniteDuration(3, MINUTES))),
      ticker)

    cache.getOrCreate("key")("value")

    currentTime += FiniteDuration(4, MINUTES)
    cache.getOrCreate("key")("newValue") shouldEqual "newValue"
  }

  it should "expire a value when the after reading expiration is set and override deadline after write" in {
    val cache = new DefaultCache[String, String](
      cacheConfig = CacheConfig(expireAfterAccess = Some(FiniteDuration(3, MINUTES))),
      ticker)

    cache.getOrCreate("key")("value")

    currentTime += FiniteDuration(1, MINUTES)
    cache.get("key")

    currentTime += FiniteDuration(2, MINUTES)
    cache.put("key")("newValue")

    currentTime += FiniteDuration(2, MINUTES)
    // put overrode expiration deadline - after access counter is reset
    cache.getOrCreate("key")("newerValue") shouldEqual "newValue"

    currentTime += FiniteDuration(3, MINUTES)
    cache.getOrCreate("key")("newerValue") shouldEqual "newerValue"
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
    case class Value(sub: String, expireAt: Deadline)
    val cache = new DefaultCache[String, Value](
      cacheConfig = CacheConfig(
        expiry = new ExpiryConfig[String, Value] {
          override def expireAfterWriteFn(key: String, value: Value, now: Deadline): Option[Deadline] =
            Some(value.expireAt)
        }),
      ticker)

    cache.getOrCreate("key1")(Value("value1", currentTime + FiniteDuration(1, MINUTES)))
    cache.getOrCreate("key2")(Value("value2", currentTime + FiniteDuration(3, MINUTES)))

    currentTime += FiniteDuration(2, MINUTES)
//todo kgd    cache.getOrCreate("key1")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should have ('sub ("newValue"))
//    cache.getOrCreate("key2")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should /*still*/ have ('sub ("value2"))

    currentTime += FiniteDuration(2, MINUTES)
//    cache.getOrCreate("key2")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should have ('sub ("newValue"))
  }
}
