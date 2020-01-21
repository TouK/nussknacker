package pl.touk.nussknacker.engine.standalone.utils.metrics.dropwizard

import io.dropwizard.metrics5.Gauge
import pl.touk.nussknacker.engine.util.metrics.GenericInstantRateMeter

class InstantRateMeter extends GenericInstantRateMeter with Gauge[Double]