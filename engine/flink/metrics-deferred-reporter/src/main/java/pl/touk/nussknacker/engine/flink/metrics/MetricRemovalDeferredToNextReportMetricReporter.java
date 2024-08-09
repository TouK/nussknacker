package pl.touk.nussknacker.engine.flink.metrics;

import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.metrics.Metric;
import org.apache.flink.metrics.MetricConfig;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.Scheduled;

import java.util.HashSet;
import java.util.Set;

public class MetricRemovalDeferredToNextReportMetricReporter implements MetricReporter, Scheduled {

    private final MetricReporter delegate;
    private final Set<Tuple3<Metric, String, MetricGroup>> metricsToRemoveAfterNextReport = new HashSet<>();

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
    public void notifyOfAddedMetric(Metric metric, String metricName, MetricGroup group) {
        delegate.notifyOfAddedMetric(metric, metricName, group);
    }

    @Override
    public synchronized void notifyOfRemovedMetric(Metric metric, String metricName, MetricGroup group) {
        metricsToRemoveAfterNextReport.add(Tuple3.of(metric, metricName, group));
    }

    @Override
    public synchronized void report() {
        ((Scheduled) delegate).report();
        //todo: maybe removal should be done even in case when delegate.report() throw some exception
        metricsToRemoveAfterNextReport.forEach(metricTuple -> delegate.notifyOfRemovedMetric(metricTuple.f0, metricTuple.f1, metricTuple.f2));
        metricsToRemoveAfterNextReport.clear();
    }
}
