package com.matiasgonsal.kafkaproducer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class JokesProducerDemo {

    private static Logger logger = LoggerFactory.getLogger(JokesProducerDemo.class);

    public static void main(String[] args) throws IOException, InterruptedException {

        OkHttpClient client = new OkHttpClient();
        String jokes_url = "http://localhost:5000/jokes"; //This is a jokes service running in python.

        KafkaProducer<String, String> producer = createKafkaProducer(); //Creates a Kafka Producer.

        Runtime.getRuntime().addShutdownHook(new Thread(() -> { //Adding a function to be executed when user stops the application.
            logger.info("Stopping producer application...");
            logger.info("Closing Kafka Producer...");
            producer.close();
            logger.info("Done!");
        }));

        while (true) {
            String joke_response = jokesService(jokes_url, client);

            ProducerRecord<String, String> record = new ProducerRecord("jokes_topic", null, joke_response); //record to be sent by the producer...
            producer.send(record, new Callback() {
                public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                    if (e != null) {
                        logger.info("Something went wrong producing messages to Kafka", e);
                    }
                }
            });
            logger.info(record.value());
            TimeUnit.MILLISECONDS.sleep(500);
        }
    }


    public static String jokesService(String url, OkHttpClient client) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public static KafkaProducer<String, String> createKafkaProducer (){
        String bootstrapServers = "localhost:9092";

        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());


        //Create a safe and idempotence producer
        properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, String.valueOf(true));// Creates an Idempotence producer.
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));



        KafkaProducer<String, String> producer = new KafkaProducer(properties);
        return producer;
    }


}
