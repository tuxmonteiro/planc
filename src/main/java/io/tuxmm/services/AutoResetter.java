/**
 *
 */

package io.tuxmm.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@EnableScheduling
public class AutoResetter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Router router;

    @Autowired
    public AutoResetter(final Router router) {
        this.router = router;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");
    }

    @Scheduled(fixedRate = 5000)
    public void periodicReset() {
        if (needResetAll()) {
            router.resetAll();
        }
    }

    // TODO: verify if is necessary reset
    private boolean needResetAll() {
        return true;
    }

}
