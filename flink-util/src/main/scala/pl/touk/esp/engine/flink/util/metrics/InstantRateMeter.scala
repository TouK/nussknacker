package pl.touk.esp.engine.flink.util.metrics

import org.apache.flink.metrics.Gauge

class InstantRateMeter extends  pl.touk.esp.engine.util.metrics.GenericInstantRateMeter with Gauge[Double]

