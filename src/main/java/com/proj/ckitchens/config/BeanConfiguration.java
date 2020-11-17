package com.proj.ckitchens.config;

import com.proj.ckitchens.common.Temperature;
import com.proj.ckitchens.model.Shelf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Configuration
public class BeanConfiguration {

    @Bean
    public Shelf hotShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.HOT.name());
    }

    @Bean
    public Shelf coldShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.COLD.name());
    }

    @Bean
    public Shelf frozenShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 10, Temperature.FROZEN.name());
    }

    @Bean
    public Shelf overflowShelf() {
        Lock lock = new ReentrantLock(true);
        return new Shelf(lock, 15, "Overflow");
    }
}
