/**
 *
 */

package io.github.tuxmonteiro.planc.configurations;

import io.undertow.server.handlers.IPAddressAccessControlHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class IPAddressAccessControlHandlerConfiguration {

    @Bean
    @Scope("prototype")
    public IPAddressAccessControlHandler ipAddressAccessControlHandler() {
        return new IPAddressAccessControlHandler();
    }
}
