package pl.touk.esp.engine.process

import pl.touk.esp.engine.process.util.StateConfiguration.RocksDBStateBackendConfig
import scala.concurrent.duration._

case class FlinkProcessConfig(checkpointInterval: FiniteDuration,
                              rocksDB: Option[RocksDBStateBackendConfig] = None,
                              eventTimeMetricSlideDuration: FiniteDuration = 10 seconds,
                              enableObjectReuse: Boolean = true) {

}
