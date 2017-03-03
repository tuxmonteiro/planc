/**
 *
 */

package io.tuxmm.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class Router {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void run() throws InterruptedException {
        logger.info(Router.class.getSimpleName() + " started");
        Thread.sleep(600000L);
    }
}
