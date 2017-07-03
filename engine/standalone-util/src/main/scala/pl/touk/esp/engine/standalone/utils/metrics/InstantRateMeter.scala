package pl.touk.esp.engine.standalone.utils.metrics

import com.codahale.metrics.Gauge
import pl.touk.esp.engine.util.metrics.GenericInstantRateMeter

class InstantRateMeter extends GenericInstantRateMeter with Gauge[Double]