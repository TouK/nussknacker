package pl.touk.nussknacker.engine.javaapi.process;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import com.typesafe.config.Config;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender;
import pl.touk.nussknacker.engine.api.Service;

public interface ProcessConfigCreator extends Serializable {

    Map<String, WithCategories<Service>> services(Config config);
    Map<String, WithCategories<SourceFactory<?>>> sourceFactories(Config config);
    Map<String, WithCategories<SinkFactory>> sinkFactories(Config config);
    Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(Config config);
    Map<String, WithCategories<ProcessSignalSender>> signals(Config config);
    Collection<ProcessListener> listeners(Config config);
    ExceptionHandlerFactory exceptionHandlerFactory(Config config);
    Map<String, WithCategories<Object>> globalProcessVariables(Config config);
    Map<String, String> buildInfo();

}
