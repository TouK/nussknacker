package pl.touk.nussknacker.engine.management.jobrunner.livedata

import com.github.benmanes.caffeine.cache.Caffeine
import io.circe.Json
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.testmode.TestProcess._

import scala.compat.java8.FunctionConverters.asJavaFunction

object LiveDataCollectingListenerHolder {

  private val listeners =
    Caffeine
      .newBuilder()
      .expireAfterAccess(java.time.Duration.ofMinutes(30))
      .build[String, LiveDataCollectingListenerStorage]()

  def results(processName: ProcessName): TestResults[Json] = {
    val values = storage(processName).values
    TestResults.aggregate(values)
  }

  private[livedata] def storage(processName: ProcessName): LiveDataCollectingListenerStorage = {
    listeners.get(processName.value, asJavaFunction((_: String) => new LiveDataCollectingListenerStorage))
  }

}
