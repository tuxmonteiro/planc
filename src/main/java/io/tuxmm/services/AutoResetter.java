/**
 *
 */

package io.tuxmm.services;

import io.tuxmm.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@EnableScheduling
public class AutoResetter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Router router;
    private final StringRedisTemplate template;

    @Autowired
    public AutoResetter(final Router router, final StringRedisTemplate template) {
        this.router = router;
        this.template = template;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");
    }

    @Scheduled(fixedRate = 5000)
    public void periodicReset() {
        String keyResetAll = Application.PREFIX + "@reset@ALL";
        if (template.hasKey(Application.PREFIX + "@reset@ALL")) {
            template.delete(keyResetAll);
            router.resetAll();
            return;
        }
        template.keys(Application.PREFIX + "@reset@*").forEach(key -> {
            template.delete(key);
            int fromIndex = key.lastIndexOf('@');
            String virtualhost = key.substring(fromIndex + 1, key.length());
            router.reset(virtualhost);
        });
    }

}
