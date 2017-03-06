/**
 *
 */

package io.tuxmm.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.boot.etcd.EtcdClient;

@Configuration
public class EtcdConfiguration {

    @Bean
    public EtcdClient etcdClient() {
        return new EtcdClient("http://127.0.0.1:2379");
    }
}
