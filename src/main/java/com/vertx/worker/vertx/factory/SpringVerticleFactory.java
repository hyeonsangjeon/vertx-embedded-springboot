/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.vertx.worker.vertx.factory;

import java.util.concurrent.Callable;

import io.vertx.core.Deployable;
import io.vertx.core.Promise;
import io.vertx.core.spi.VerticleFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * A {@link VerticleFactory} backed by Spring's {@link ApplicationContext}.
 *
 * @author Thomas Segismont
 */
@Component
public class SpringVerticleFactory implements VerticleFactory {

    private final ApplicationContext applicationContext;

    public SpringVerticleFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public String prefix() {
        return "book";
    }

    @Override
    public void createVerticle2(String verticleName, ClassLoader classLoader,
                                Promise<Callable<? extends Deployable>> promise) {
        String className = VerticleFactory.removePrefix(verticleName);
        try {
            Class<? extends Deployable> verticleClass = classLoader.loadClass(className).asSubclass(Deployable.class);
            promise.complete(() -> applicationContext.getBean(verticleClass));
        } catch (ClassNotFoundException | ClassCastException e) {
            promise.fail(e);
        }
    }
}
