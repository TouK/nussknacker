package pl.touk.nussknacker.engine.javaapi.process;

import com.typesafe.config.Config;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory;
import pl.touk.nussknacker.engine.api.process.*;
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProcessConfigCreator extends Serializable {

    Map<String, WithCategories<Service>> services(Config config);
    Map<String, WithCategories<SourceFactory<?>>> sourceFactories(Config config);
    Map<String, WithCategories<SinkFactory>> sinkFactories(Config config);
    Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(Config config);
    Map<String, WithCategories<ProcessSignalSender>> signals(Config config);
    Collection<ProcessListener> listeners(Config config);
    ExceptionHandlerFactory exceptionHandlerFactory(Config config);
    ExpressionConfig expressionConfig(Config config);
    Map<String, String> buildInfo();
    default Optional<AsyncExecutionContextPreparer> asyncExecutionContextPreparer(Config config) {
      return Optional.empty();
    }
    default ClassExtractionSettings classExtractionSettings(Config config) {
        return ClassExtractionSettings.DEFAULT;
    }

}
