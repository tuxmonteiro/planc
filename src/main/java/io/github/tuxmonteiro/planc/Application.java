/**
 *
 */

package io.github.tuxmonteiro.planc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public final static String PREFIX = System.getProperty("APP_PREFIX", "PLANC");

    public static void main(String args[]) throws Exception {
        SpringApplication.run(Application.class, args);
    }
}
