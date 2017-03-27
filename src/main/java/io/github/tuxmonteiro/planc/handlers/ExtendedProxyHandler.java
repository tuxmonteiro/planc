/**
 *
 */
package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.client.hostselectors.HostSelector;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.ResponseTimeAttribute;
import io.undertow.attribute.SubstituteEmptyWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtendedProxyHandler implements HttpHandler, ProcessorLocalStatusCode {

    private static final String UNKNOWN = "UNKNOWN";
    private static final String REAL_DEST = "#REAL_DEST#";
    private static final String LOGPATTERN = "%a\t%v\t%r\t-\t-\tLocal:\t%s\t*-\t%B\t%D\tProxy:\t"+ REAL_DEST +"\t%s\t-\t%b\t-\t-" +
            "\tAgent:\t%{i,User-Agent}\tFwd:\t%{i,X-Forwarded-For}";

    private final Log logger = LogFactory.getLog(this.getClass());

    private final ExchangeAttribute tokens = ExchangeAttributes.parser(getClass().getClassLoader(), new SubstituteEmptyWrapper("-")).parse(LOGPATTERN);
    private final AccessLogCompletionListener accessLogCompletionListener = new AccessLogCompletionListener();
    private final ResponseTimeAttribute responseTimeAttribute = new ResponseTimeAttribute(TimeUnit.MILLISECONDS);
    private int maxRequestTime = Integer.MAX_VALUE - 1;
    private final ProxyHandler proxyHandler;

    public ExtendedProxyHandler(ProxyClient proxyClient, int maxRequestTime, HttpHandler next) {
        proxyHandler = new ProxyHandler(proxyClient, maxRequestTime, next);
    }

    public ExtendedProxyHandler(ProxyClient proxyClient, HttpHandler defaultHandler) {
        proxyHandler = new ProxyHandler(proxyClient, defaultHandler);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(accessLogCompletionListener);
        proxyHandler.handleRequest(exchange);
    }


    private class AccessLogCompletionListener implements ExchangeCompletionListener {

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                final String tempRealDest = exchange.getAttachment(HostSelector.REAL_DEST);
                String realDest = tempRealDest != null ? tempRealDest : UNKNOWN;
                String message = tokens.readAttribute(exchange);
                int realStatus = exchange.getStatusCode();
                long responseBytesSent = exchange.getResponseBytesSent();
                final Integer responseTime = Math.round(Float.parseFloat(responseTimeAttribute.readAttribute(exchange)));
                int fakeStatusCode = getFakeStatusCode(tempRealDest, realStatus, responseBytesSent, responseTime, maxRequestTime);
                if (fakeStatusCode != NOT_MODIFIED) {
                    message = message.replaceAll("^(.*Local:\t)\\d{3}(\t.*Proxy:\t.*\t)\\d{3}(\t.*)$",
                            "$1" + String.valueOf(fakeStatusCode) + "$2" + String.valueOf(fakeStatusCode) + "$3");
                }
                Pattern compile = Pattern.compile("([^\\t]*\\t[^\\t]*\\t)([^\\t]+)(\\t.*)$");
                Matcher match = compile.matcher(message);
                if (match.find()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(match.group(1)).append(match.group(2).replace(" ", "\t")).append(match.group(3));
                    message = sb.toString();
                }
                logger.info(message.replaceAll(REAL_DEST, realDest));
            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                nextListener.proceed();
            }
        }
    }
}
