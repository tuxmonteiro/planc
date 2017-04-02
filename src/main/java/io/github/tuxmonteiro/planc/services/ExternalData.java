package io.github.tuxmonteiro.planc.services;

import io.github.tuxmonteiro.planc.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.zalando.boot.etcd.EtcdClient;
import org.zalando.boot.etcd.EtcdNode;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ExternalData {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String ROOT_KEY         = "/";
    public static final String PREFIX_KEY       = ROOT_KEY + Application.PREFIX;
    public static final String VIRTUALHOSTS_KEY = PREFIX_KEY + "/virtualhosts";
    public static final String POOLS_KEY        = PREFIX_KEY + "/pools";
    public static final String RESET_ALL_KEY    = PREFIX_KEY + "/reset_all";

    private final EtcdClient client;
    private final EtcdNode nullNode = new EtcdNode();
    private final EtcdNode emptyNode = nullNode;
    private final EtcdNode undefNode = nullNode;

    public ExternalData(@Value("#{etcdClient}") final EtcdClient template) {
        this.client = template;
        emptyNode.setValue("");
        undefNode.setValue("UNDEF");
    }

    public EtcdClient client() {
        return client;
    }

    public List<EtcdNode> listFrom(String key) {
        return listFrom(key, false);
    }

    public List<EtcdNode> listFrom(String key, boolean recursive) {
        return listFrom(node(key, recursive));
    }

    public List<EtcdNode> listFrom(EtcdNode node) {
        return Optional.ofNullable(node.getNodes()).orElse(Collections.emptyList());
    }

    public EtcdNode node(String key) {
        return node(key, false);
    }

    public EtcdNode node(String key, boolean recursive) {
        try {
            return client.get(key, recursive).getNode();
        } catch (Exception e) {
            logger.error(e.getMessage());
            return nullNode;
        }
    }

    public EtcdNode nullNode() {
        return nullNode;
    }

    public EtcdNode emptyNode() {
        return emptyNode;
    }

    public EtcdNode undefNode() {
        return undefNode;
    }

    public boolean exist(String key) {
        final EtcdNode node = node(key);
        return node.getValue() != null || node.isDir();
    }

    public boolean endsWith(final EtcdNode node, String key) {
        return node.getKey().endsWith(key);
    }

}
