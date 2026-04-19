package com.dadry.finpal;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FinPalApplication {

    public static void main(String[] args) {
        // PostgreSQL rejects the deprecated "Europe/Kiev" zone id on newer setups.
        // Set a valid JVM default before the datasource is initialized.
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"));
        SpringApplication.run(FinPalApplication.class, args);
    }

}
