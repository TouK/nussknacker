package pl.touk.nussknacker.engine.util.cache

import com.github.benmanes.caffeine.cache.Ticker
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration.{DAYS, Deadline, Duration, FiniteDuration, HOURS, MINUTES}

class DefaultCacheTest extends FlatSpec with Matchers with VeryPatientScalaFutures{

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
    case class Value(sub: String, exp: Deadline)
    val cache = new DefaultCache[String, Value](
      cacheConfig = CacheConfig(
        expiry = new ExpiryConfig[String, Value] {
          override def expireAfterWriteFn(key: String, value: Value, now: Deadline): Option[Deadline] =
            Some(value.exp)
        }),
      ticker)

    cache.getOrCreate("key1")(Value("value1", currentTime + FiniteDuration(1, MINUTES)))
    cache.getOrCreate("key2")(Value("value2", currentTime + FiniteDuration(3, MINUTES)))

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key1")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should have ('sub ("newValue"))
    cache.getOrCreate("key2")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should /*still*/ have ('sub ("value2"))

    currentTime += FiniteDuration(2, MINUTES)
    cache.getOrCreate("key2")(Value("newValue", currentTime + FiniteDuration(1, HOURS))) should have ('sub ("newValue"))
  }

  it should "allow setting expiration time depending on a value for an async cache" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    case class Value(sub: String, exp: Deadline)
    val cache = new DefaultAsyncCache[String, Value](
      cacheConfig = CacheConfig(
        expiry = new ExpiryConfig[String, Value] {
          override def expireAfterWriteFn(key: String, value: Value, now: Deadline): Option[Deadline] =
            Some(value.exp)
        }),
      ticker)(global)

    cache.getOrCreate("key1")(Future.successful(Value("value1", currentTime + FiniteDuration(1, MINUTES))))
    cache.getOrCreate("key2")(Future.successful(Value("value2", currentTime + FiniteDuration(3, MINUTES))))

    currentTime += FiniteDuration(2, MINUTES)
    whenReady(cache.getOrCreate("key1")(Future.successful(Value("newValue1", currentTime + FiniteDuration(1, HOURS))))) {
      _ should have ('sub("newValue1"))
    }
    whenReady(cache.getOrCreate("key2")(Future.successful(Value("newValue2", currentTime + FiniteDuration(1, HOURS))))) {
      _ should /*still*/ have ('sub ("value2"))
    }

    currentTime += FiniteDuration(2, MINUTES)
    whenReady(cache.getOrCreate("key2")(Future.successful(Value("newValue2", currentTime + FiniteDuration(1, HOURS))))) {
      _ should have ('sub ("newValue2"))
    }
  }
}
