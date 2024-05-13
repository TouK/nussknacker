package pl.touk.nussknacker.ui.db.timeseries.questdb

import io.questdb.TelemetryConfiguration
import io.questdb.cairo.DefaultCairoConfiguration
import io.questdb.cairo.sql.SqlExecutionCircuitBreakerConfiguration
import io.questdb.griffin.DefaultSqlExecutionCircuitBreakerConfiguration

import java.util.concurrent.TimeUnit

private[questdb] class CustomCairoConfiguration(private val root: String) extends DefaultCairoConfiguration(root) {

  override def getTelemetryConfiguration: TelemetryConfiguration = new TelemetryConfiguration {
    override def getDisableCompletely: Boolean = true
    override def getEnabled: Boolean           = false
    override def getQueueCapacity: Int         = 16
    override def hideTables(): Boolean         = false
  }

  override def getCircuitBreakerConfiguration: SqlExecutionCircuitBreakerConfiguration =
    new DefaultSqlExecutionCircuitBreakerConfiguration {
      // timeout in micros
      override def getQueryTimeout: Long = TimeUnit.SECONDS.toMicros(10L)
    }

}
