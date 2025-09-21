package org.Durit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
//        System.setProperty("java.awt.headless", "false");
        SpringApplication app = new SpringApplication(App.class);
        app.setHeadless(false);
        app.run(args);

    }
}
