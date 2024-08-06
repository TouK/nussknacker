package pl.touk.nussknacker.engine.flink.metrics;

import org.apache.flink.metrics.reporter.MetricReporter;
import org.apache.flink.metrics.reporter.MetricReporterFactory;

import java.util.*;

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
