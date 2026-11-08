package tn.esprit.energyshare.marketplace.config;

import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoConnectionTest {

    @Bean
    CommandLineRunner testConnection(MongoClient mongoClient) {
        String uri = System.getenv("MONGODB_URI");

        System.out.println(
                "MongoDB URI variable present: " + (uri != null)
        );

        System.out.println(
                "MongoDB URI prefix valid: " +
                        (uri != null &&
                                (uri.startsWith("mongodb://") ||
                                        uri.startsWith("mongodb+srv://")))
        );
        return args -> {
            Document result = mongoClient
                    .getDatabase("energy_marketplace_db")
                    .runCommand(new Document("ping", 1));

            System.out.println("MongoDB Atlas connected: "
                    + result.toJson());
        };
    }
}