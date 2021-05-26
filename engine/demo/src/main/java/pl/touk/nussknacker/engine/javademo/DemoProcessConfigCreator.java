package pl.touk.nussknacker.engine.javademo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.ProcessListener;
import pl.touk.nussknacker.engine.api.Service;
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory;
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies;
import pl.touk.nussknacker.engine.api.process.SingleNodeConfig$;
import pl.touk.nussknacker.engine.api.process.SinkFactory;
import pl.touk.nussknacker.engine.api.process.SourceFactory;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender;
import pl.touk.nussknacker.engine.demo.LoggingExceptionHandlerFactory;
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler;
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler;
import pl.touk.nussknacker.engine.javaapi.process.ExpressionConfig;
import pl.touk.nussknacker.engine.javaapi.process.ProcessConfigCreator;
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory;
import pl.touk.nussknacker.engine.kafka.generic.sources;
import pl.touk.nussknacker.engine.kafka.serialization.KafkaSerializationSchemaFactory;
import pl.touk.nussknacker.engine.kafka.serialization.schemas;
import pl.touk.nussknacker.engine.kafka.sink.KafkaSinkFactory;
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory;
import scala.Option;
import scala.collection.JavaConverters;
import scala.reflect.ClassTag$;

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
    public Map<String, WithCategories<Service>> services(ProcessObjectDependencies processObjectDependencies) {
        Map<String, WithCategories<Service>> m = new HashMap<>();
        m.put("clientService", all(new ClientService()));
        return m;
    }

    @Override
    public Map<String, WithCategories<SourceFactory<?>>> sourceFactories(ProcessObjectDependencies processObjectDependencies) {
        KafkaSourceFactory<String, Transaction> sourceFactory = getTransactionKafkaSourceFactory(processObjectDependencies);
        Map<String, WithCategories<SourceFactory<?>>> m = new HashMap<>();
        m.put("kafka-transaction", all(sourceFactory));
        return m;
    }

    private KafkaSourceFactory<String, Transaction> getTransactionKafkaSourceFactory(ProcessObjectDependencies processObjectDependencies) {
        TimestampWatermarkHandler<ConsumerRecord<String, Transaction>> extractor = new StandardTimestampWatermarkHandler<>(WatermarkStrategy
            .<ConsumerRecord<String, Transaction>>forBoundedOutOfOrderness(Duration.ofMinutes(10))
            .withTimestampAssigner((SerializableTimestampAssigner<ConsumerRecord<String, Transaction>>) (element, recordTimestamp) -> element.value().eventDate));

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
                new FixedValueDeserializationSchemaFactory<>(schema),
                Option.apply(extractor),
                sources.FixedRecordFormatterFactoryWrapper$.MODULE$.apply(sources.JsonRecordFormatter$.MODULE$),
                processObjectDependencies,
                ClassTag$.MODULE$.apply(String.class),
                ClassTag$.MODULE$.apply(Transaction.class)
        );
    }

    @Override
    public Map<String, WithCategories<SinkFactory>> sinkFactories(ProcessObjectDependencies processObjectDependencies) {
        schemas.ToStringSerializer<Object> serializer = element -> {
            if (element instanceof String) {
                return (String) element;
            } else {
                throw new RuntimeException("Sorry, only strings");
            }
        };
        KafkaSerializationSchemaFactory<Object> schema = (topic, kafkaConfig1) ->
            new schemas.SimpleSerializationSchema<>(topic, serializer, null);
        KafkaSinkFactory factory = new KafkaSinkFactory(schema, processObjectDependencies);
        Map<String, WithCategories<SinkFactory>> m = new HashMap<>();
        m.put("kafka-stringSink", all(factory));
        return m;
    }

    @Override
    public Map<String, WithCategories<CustomStreamTransformer>> customStreamTransformers(ProcessObjectDependencies processObjectDependencies) {
        Map<String, WithCategories<CustomStreamTransformer>> m = new HashMap<>();
        m.put("eventsCounter", all(new EventsCounter()));
        m.put("transactionAmountAggregator", all(new TransactionAmountAggregator()));
        return m;
    }

    @Override
    public Map<String, WithCategories<ProcessSignalSender>> signals(ProcessObjectDependencies processObjectDependencies) {
        return Collections.emptyMap();
    }

    @Override
    public Collection<ProcessListener> listeners(ProcessObjectDependencies processObjectDependencies) {
        return Collections.emptyList();
    }

    @Override
    public ExceptionHandlerFactory exceptionHandlerFactory(ProcessObjectDependencies processObjectDependencies) {
        return new LoggingExceptionHandlerFactory(processObjectDependencies.config());
    }

    @Override
    public ExpressionConfig expressionConfig(ProcessObjectDependencies processObjectDependencies) {
        return new ExpressionConfig(
                Collections.singletonMap("UTIL", all(new UtilProcessHelper())),
                Collections.emptyList()
        );
    }

    @Override
    public Map<String, String> buildInfo() {
        return Collections.emptyMap();
    }

}
