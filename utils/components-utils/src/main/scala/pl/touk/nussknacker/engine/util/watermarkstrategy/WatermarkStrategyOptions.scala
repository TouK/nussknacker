package pl.touk.nussknacker.engine.util.watermarkstrategy

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Source

import java.time.{Duration, Instant}

// TODO: Add watermark alignment options
class WatermarkStrategyOptions(
    val eventTimeLazyParam: LazyParameter[Instant],
    val maxOutOfOrderness: Option[Duration],
    val idleTimeout: Option[Duration]
)

trait WithWatermarkStrategyOptions { self: Source =>

  def watermarkStrategyOptions: WatermarkStrategyOptions

}
