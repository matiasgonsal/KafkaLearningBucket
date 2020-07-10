package com.matiasgonsal.kafkaconsumer;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class JokesConsumerDemo {

    private static Logger logger = LoggerFactory.getLogger(JokesConsumerDemo.class);

    public static void main(String[] args) throws IOException {
        RestHighLevelClient elasticSearchClient = createElasticSearchClient();
        KafkaConsumer<String, String> jokesConsumer = createKafkaConsumer("jokes_topic");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> { //Adding a function to be executed when user stops the application.
            logger.info("Stopping consumer application...");
            logger.info("Closing Kafka Consumer...");
            try {
                elasticSearchClient.close();
                jokesConsumer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.info("Done!");
        }));

        while (true) {
            ConsumerRecords<String, String> records = jokesConsumer.poll(Duration.ofMillis(100));
            for (ConsumerRecord<String, String> record : records){
                logger.info("Topic: " + record.topic());
                logger.info("Key: " + record.key());
                logger.info("Offset: " + record.offset());
                logger.info("Value: " + record.value());

                String elsId = insertElasticSearchDocument("jokes", record.value(), elasticSearchClient);
                logger.info("Record inserted in ElasticSearch with id: " + elsId);
            }
        }
    }

    private static String insertElasticSearchDocument (String index, String source, RestHighLevelClient elasticSearchClient) throws IOException {
        IndexRequest indexRequest = new IndexRequest(index);
        indexRequest.source(source, XContentType.JSON);

        IndexResponse indexResponse = elasticSearchClient.index(indexRequest, RequestOptions.DEFAULT);
        return indexResponse.getId();
    }

    private static KafkaConsumer<String, String> createKafkaConsumer (String topic){
        String bootstrapServers = "localhost:9092";
        String groupId = "ElasticSearchJokesMigration";

        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");


        KafkaConsumer<String, String> consumer = new KafkaConsumer(properties);
        consumer.subscribe(Arrays.asList(topic));
        return consumer;
    }


    private static RestHighLevelClient createElasticSearchClient (){
        String connString = "https://uv07eesby9:tfarn1qfws@learning-els-testing-4526254182.us-east-1.bonsaisearch.net:443";
        URI connUri = URI.create(connString);
        String[] auth = connUri.getUserInfo().split(":");

        CredentialsProvider cp = new BasicCredentialsProvider();
        cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));

        RestHighLevelClient rhlc = new RestHighLevelClient(
                RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme()))
                        .setHttpClientConfigCallback(
                                httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(cp)
                                        .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())));

        return rhlc;
    }
}
