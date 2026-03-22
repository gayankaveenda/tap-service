package au.com.transport.tapservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TapServiceApplication.class, args);
    }

}
