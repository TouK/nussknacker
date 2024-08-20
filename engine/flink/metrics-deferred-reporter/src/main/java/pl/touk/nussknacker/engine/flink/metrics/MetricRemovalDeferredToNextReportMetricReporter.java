package pl.touk.nussknacker.engine.flink.metrics;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.Scheduled;

import java.util.HashMap;
import java.util.Map;

public class MetricRemovalDeferredToNextReportMetricReporter implements MetricReporter, Scheduled {

    private final MetricReporter delegate;
    private final Map<Metric, Tuple2<String, MetricGroup>> metricsToRemoveAfterNextReport = new HashMap<>();

    public MetricRemovalDeferredToNextReportMetricReporter(MetricReporter delegate) {
        if (!(delegate instanceof Scheduled)) {
            throw new IllegalStateException("Only Scheduled reporters are supported");
        }
        this.delegate = delegate;
    }

    @Override
    public void open(MetricConfig config) {
        delegate.open(config);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public synchronized void notifyOfAddedMetric(Metric metric, String metricName, MetricGroup group) {
        delegate.notifyOfAddedMetric(metric, metricName, group);
        metricsToRemoveAfterNextReport.remove(metric);
    }

    @Override
    public synchronized void notifyOfRemovedMetric(Metric metric, String metricName, MetricGroup group) {
        metricsToRemoveAfterNextReport.put(metric, Tuple2.of(metricName, group));
    }

    @Override
    public synchronized void report() {
        try {
            ((Scheduled) delegate).report();
        } finally {
            metricsToRemoveAfterNextReport.forEach((key, value) -> delegate.notifyOfRemovedMetric(key, value.f0, value.f1));
            metricsToRemoveAfterNextReport.clear();
        }
    }
}
