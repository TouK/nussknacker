package pl.touk.nussknacker.engine.management.javasample;

import com.typesafe.config.Config;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory;
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender;
import pl.touk.nussknacker.engine.javaapi.process.ExpressionConfig;
import pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator;

public class TestProcessConfigCreator implements ProcessConfigCreator {

    private Objects objects = new Objects();

    @Override
    public Map<String, WithCategories<Service>> services(Config config) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, WithCategories<SourceFactory<?>>> sourceFactories(Config config, ObjectNaming objectNaming) {
        return Collections.singletonMap("source", objects.source());
    }

    @Override
    public Map<String, WithCategories<SinkFactory>> sinkFactories(Config config, ObjectNaming objectNaming) {
        return Collections.singletonMap("sink", objects.sink());
    }

    @Override
    public Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(Config config) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, WithCategories<ProcessSignalSender>> signals(Config config) {
        return Collections.emptyMap();
    }

    @Override
    public Collection<ProcessListener> listeners(Config config) {
        return Collections.emptyList();
    }

    @Override
    public ExceptionHandlerFactory exceptionHandlerFactory(Config config) {
        return objects.exceptionHandler();
    }

    @Override
    public ExpressionConfig expressionConfig(Config config) {
        return new ExpressionConfig(Collections.emptyMap(), Collections.emptyList());
    }

    @Override
    public Map<String, String> buildInfo() {
        return Collections.emptyMap();
    }
}
