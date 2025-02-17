package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.process.*;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProcessConfigCreator extends Serializable {

    Map<String, WithCategories<Service>> services(ProcessObjectDependencies processObjectDependencies);
    Map<String, WithCategories<SourceFactory>> sourceFactories(ProcessObjectDependencies processObjectDependencies);
    Map<String, WithCategories<SinkFactory>> sinkFactories(ProcessObjectDependencies processObjectDependencies);
    Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(ProcessObjectDependencies processObjectDependencies);
    Collection<ProcessListener> listeners(ProcessObjectDependencies processObjectDependencies);
    ExpressionConfig expressionConfig(ProcessObjectDependencies processObjectDependencies);
    Map<String, String> modelInfo();
    default Optional<AsyncExecutionContextPreparer> asyncExecutionContextPreparer(ProcessObjectDependencies processObjectDependencies) {
      return Optional.empty();
    }
    default ClassExtractionSettings classExtractionSettings(ProcessObjectDependencies processObjectDependencies) {
        return ClassExtractionSettings.DEFAULT;
    }

}
