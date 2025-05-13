package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.ModelConfig;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ProcessConfigCreator extends Serializable {

    Map<String, WithCategories<Service>> services(ModelConfig modelConfig);
    Map<String, WithCategories<SourceFactory>> sourceFactories(ModelConfig modelConfig);
    Map<String, WithCategories<SinkFactory>> sinkFactories(ModelConfig modelConfig);
    Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(ModelConfig modelConfig);
    Collection<ProcessListener> listeners(ModelConfig modelConfig);
    ExpressionConfig expressionConfig(ModelConfig modelConfig);
    Map<String, String> modelInfo();
    default Optional<AsyncExecutionContextPreparer> asyncExecutionContextPreparer(ModelConfig modelConfig) {
      return Optional.empty();
    }
    default ClassExtractionSettings classExtractionSettings(ModelConfig modelConfig) {
        return ClassExtractionSettings.DEFAULT;
    }

}
