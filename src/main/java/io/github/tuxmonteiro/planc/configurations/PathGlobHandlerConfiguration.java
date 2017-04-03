/**
 *
 */

package io.github.tuxmonteiro.planc.configurations;

import io.github.tuxmonteiro.planc.handlers.PathGlobHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class PathGlobHandlerConfiguration {

    @Bean
    @Scope("prototype")
    public PathGlobHandler pathGlobHandler() {
        return new PathGlobHandler(ResponseCodeHandler.HANDLE_500);
    }
}
