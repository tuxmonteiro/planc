package io.github.tuxmonteiro.planc.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Starter {

    public Starter(@Autowired final Router router) {
        router.getUndertow().start();
    }
}
