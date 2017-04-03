/**
 *
 */

package io.github.tuxmonteiro.planc.configurations;

import io.undertow.server.handlers.NameVirtualHostHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class NameVirtualHostHandlerConfiguration {

    @Bean
    @Scope("singleton")
    public NameVirtualHostHandler nameVirtualHostHandler() {
        return new NameVirtualHostHandler();
    }

}
