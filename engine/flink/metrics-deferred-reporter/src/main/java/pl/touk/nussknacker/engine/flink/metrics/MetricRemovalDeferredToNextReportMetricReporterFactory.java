package pl.touk.nussknacker.engine.flink.metrics;

import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.MetricReporterFactory;

import java.util.*;

/*
* Flink by design doesn't flush metrics after job finish but only unregisters them from shared MetricRegistry which can leads to metrics incompleteness or even no metrics for short living jobs.
* To deal with it MetricRemovalDeferredToNextReportMetricReporter can be used to defer metrics removal to the next report call on MetricReporter
* to ensure that metrics from closed jobs will be flushed with last known values.
*
* Usage:
* 1. Add nussknacker-flink-metrics-deferred-reporter.jar to dir with reporter to wrap (e.g. plugins/metrics-influx) in jobmanager and taskmanager
* 2. Point to MetricRemovalDeferredToNextReportMetricReporterFactory and wrapped reporter in flink config e.g:
*     metrics.reporter.influxdb_reporter.factory.class: pl.touk.nussknacker.engine.flink.metrics.MetricRemovalDeferredToNextReportMetricReporterFactory
*     metrics.reporter.influxdb_reporter.delegate.factory.class: org.apache.flink.metrics.influxdb.InfluxdbReporterFactory
*     metrics.reporter.influxdb_reporter.<other influx specific configs>: ...
* */
public class MetricRemovalDeferredToNextReportMetricReporterFactory implements MetricReporterFactory {
    private static final String DELEGATE_CLASS_PROPERTY_NAME = "delegate.factory.class";


    @Override
    public MetricReporter createMetricReporter(Properties properties) {
        String delegateFactoryClassName = Optional.ofNullable(properties.getProperty(DELEGATE_CLASS_PROPERTY_NAME))
                .orElseThrow(() -> new IllegalStateException(
                        DELEGATE_CLASS_PROPERTY_NAME + " property must be provided when using " + this.getClass().getSimpleName()
                ));

        MetricReporterFactory delegateReporterFactory = Optional.ofNullable(loadAllFactories().get(delegateFactoryClassName))
                .orElseThrow(() -> new IllegalStateException(
                        "Factory class: " + delegateFactoryClassName + " specified in " + DELEGATE_CLASS_PROPERTY_NAME + " not found"
                ));

        MetricReporter delegateReporter = delegateReporterFactory.createMetricReporter(properties);
        return new MetricRemovalDeferredToNextReportMetricReporter(delegateReporter);
    }

    private Map<String, MetricReporterFactory> loadAllFactories() {
        Map<String, MetricReporterFactory> factories = new HashMap<>();
        ServiceLoader.load(MetricReporterFactory.class, MetricRemovalDeferredToNextReportMetricReporterFactory.class.getClassLoader())
                .forEach(factory -> {
                    factories.put(factory.getClass().getName(), factory);
                });
        return factories;
    }
}
