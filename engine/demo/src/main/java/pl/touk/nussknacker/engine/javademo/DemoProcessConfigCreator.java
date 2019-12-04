package pl.touk.nussknacker.engine.javademo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory;
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig$;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender;
import pl.touk.nussknacker.engine.api.test.TestParsingUtils;
import pl.touk.nussknacker.engine.demo.LoggingExceptionHandlerFactory;
import pl.touk.nussknacker.engine.javaapi.process.ExpressionConfig;
import pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator;
import pl.touk.nussknacker.engine.kafka.KafkaConfig;
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory;
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactory;
import pl.touk.nussknacker.engine.kafka.serialization.SerializationSchemaFactory;
import pl.touk.nussknacker.engine.kafka.serialization.schemas;
import scala.Option;
import scala.collection.JavaConverters;

public class DemoProcessConfigCreator implements ProcessConfigCreator {

    private <T> WithCategories<T> all(T value) {
        final ArrayList<String> objects = new ArrayList<>();
        objects.add("Recommendations");
        objects.add("FraudDetection");
        return new WithCategories<>(
                value,
                JavaConverters.collectionAsScalaIterableConverter(objects).asScala().toList(),
                SingleNodeConfig$.MODULE$.zero());
    }

    @Override
    public Map<String, WithCategories<Service>> services(Config config) {
        Map<String, WithCategories<Service>> m = new HashMap<>();
        m.put("clientService", all(new ClientService()));
        return m;
    }

    @Override
    public Map<String, WithCategories<SourceFactory<?>>> sourceFactories(Config config) {
        KafkaConfig kafkaConfig = getKafkaConfig(config);
        KafkaSourceFactory<Transaction> sourceFactory = getTransactionKafkaSourceFactory(kafkaConfig);
        Map<String, WithCategories<SourceFactory<?>>> m = new HashMap<>();
        m.put("kafka-transaction", all(sourceFactory));
        return m;
    }

    private KafkaSourceFactory<Transaction> getTransactionKafkaSourceFactory(KafkaConfig kafkaConfig) {
        BoundedOutOfOrdernessTimestampExtractor<Transaction> extractor = new BoundedOutOfOrdernessTimestampExtractor<Transaction>(Time.minutes(10)) {
            @Override
            public long extractTimestamp(Transaction element) {
                return element.eventDate;
            }
        };
        DeserializationSchema<Transaction> schema = new DeserializationSchema<Transaction>() {
            @Override
            public Transaction deserialize(byte[] message) throws IOException {
                return new ObjectMapper().readValue(message, Transaction.class);
            }

            @Override
            public boolean isEndOfStream(Transaction nextElement) {
                return false;
            }

            @Override
            public TypeInformation<Transaction> getProducedType() {
                return TypeInformation.of(Transaction.class);
            }
        };
        return new KafkaSourceFactory<>(
                kafkaConfig,
                schema,
                Option.apply(extractor),
                TestParsingUtils.newLineSplit(),
                TypeInformation.of(Transaction.class)
        );
    }

    @Override
    public Map<String, WithCategories<SinkFactory>> sinkFactories(Config config) {
        KafkaConfig kafkaConfig = getKafkaConfig(config);

        schemas.ToStringSerializer<Object> serializer = element -> {
            if (element instanceof String) {
                return (String) element;
            } else {
                throw new RuntimeException("Sorry, only strings");
            }
        };
        SerializationSchemaFactory<Object> schema = (topic, kafkaConfig1) ->
            new schemas.SimpleSerializationSchema<>(topic, serializer, null);
        KafkaSinkFactory factory = new KafkaSinkFactory(kafkaConfig, schema);
        Map<String, WithCategories<SinkFactory>> m = new HashMap<>();
        m.put("kafka-stringSink", all(factory));
        return m;
    }

    @Override
    public Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(Config config) {
        Map<String, WithCategories<CustomStreamTransformer>> m = new HashMap<>();
        m.put("eventsCounter", all(new EventsCounter()));
        m.put("transactionAmountAggregator", all(new TransactionAmountAggregator()));
        return m;
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
        return new LoggingExceptionHandlerFactory(config);
    }

    @Override
    public ExpressionConfig expressionConfig(Config config) {
        return new ExpressionConfig(
                Collections.singletonMap("UTIL", all(new UtilProcessHelper())),
                Collections.emptyList()
        );
    }

    @Override
    public Map<String, String> buildInfo() {
        return Collections.emptyMap();
    }

    private KafkaConfig getKafkaConfig(Config config) {
        return new KafkaConfig(
                config.getString("kafka.kafkaAddress"),
                Option.empty(),
                Option.empty()
        );
    }

}
