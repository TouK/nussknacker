package pl.touk.nussknacker.engine.management.javasample;

import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import pl.touk.nussknacker.engine.javaapi.process.ExpressionConfig;
import pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class TestProcessConfigCreator implements ProcessConfigCreator {

    private Objects objects = new Objects();

    @Override
    public Map<String, WithCategories<Service>> services(ProcessObjectDependencies processObjectDependencies) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, WithCategories<SourceFactory>> sourceFactories(ProcessObjectDependencies processObjectDependencies) {
        return Collections.singletonMap("source", objects.source());
    }

    @Override
    public Map<String, WithCategories<SinkFactory>> sinkFactories(ProcessObjectDependencies processObjectDependencies) {
        return Collections.singletonMap("sink", objects.sink());
    }

    @Override
    public Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(ProcessObjectDependencies processObjectDependencies) {
        return Collections.emptyMap();
    }

    @Override
    public Collection<ProcessListener> listeners(ProcessObjectDependencies processObjectDependencies) {
        return Collections.emptyList();
    }

    @Override
    public ExpressionConfig expressionConfig(ProcessObjectDependencies processObjectDependencies) {
        return new ExpressionConfig(Collections.emptyMap(), Collections.emptyList());
    }

    @Override
    public Map<String, String> modelInfo() {
        return Collections.emptyMap();
    }
}
