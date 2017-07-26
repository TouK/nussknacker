package pl.touk.nussknacker.engine.standalone.utils.metrics

import com.codahale.metrics.Gauge
import pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter

class InstantRateMeter extends GenericInstantRateMeter with Gauge[Double]