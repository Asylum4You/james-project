/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.utils;

import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class GuiceGenericLoader implements GuiceLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuiceGenericLoader.class);

    @VisibleForTesting
    public static GuiceGenericLoader forTesting(ExtendedClassLoader extendedClassLoader) {
        return new GuiceGenericLoader(Guice.createInjector(), extendedClassLoader, ExtensionConfiguration.DEFAULT);
    }

    public static class InvocationPerformer<T> implements GuiceLoader.InvocationPerformer<T> {
        private final Injector injector;
        private final ExtendedClassLoader extendedClassLoader;
        private final NamingScheme namingScheme;

        private InvocationPerformer(Injector injector, ExtendedClassLoader extendedClassLoader, NamingScheme namingScheme) {
            this.injector = injector;
            this.extendedClassLoader = extendedClassLoader;
            this.namingScheme = namingScheme;
        }

        @Override
        public T instantiate(ClassName className) throws ClassNotFoundException {
            try {
                Class<T> clazz = locateClass(className);
                return injector.getInstance(clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load " + className.getName(), e);
            }
        }

        public Class<T> locateClass(ClassName className) throws ClassNotFoundException {
            ImmutableList<Class<T>> classes = this.namingScheme.toFullyQualifiedClassNames(className)
                    .flatMap(this::tryLocateClass)
                    .collect(ImmutableList.toImmutableList());

            if (classes.size() == 0) {
                throw new ClassNotFoundException(className.getName());
            }
            if (classes.size() > 1) {
                LOGGER.warn("Ambiguous class name for {}. Corresponding classes are {} and {} will be loaded",
                        className, classes, classes.get(0));
            }
            return classes.get(0);
        }

        private Stream<Class<T>> tryLocateClass(FullyQualifiedClassName className) {
            return (Stream) extendedClassLoader.locateClass(className).stream();
        }

        @Override
        public InvocationPerformer<T> withChildModule(Module childModule) {
            return new InvocationPerformer<>(injector.createChildInjector(childModule), extendedClassLoader, namingScheme);
        }

        @Override
        public InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
            return new InvocationPerformer<>(injector, extendedClassLoader, namingSheme);
        }
    }

    private final Injector injector;
    private final ExtendedClassLoader extendedClassLoader;

    @Inject
    public GuiceGenericLoader(Injector injector, ExtendedClassLoader extendedClassLoader, ExtensionConfiguration extensionConfiguration) {

        this.extendedClassLoader = extendedClassLoader;

        Module additionalExtensionBindings = Modules.combine(extensionConfiguration.getAdditionalGuiceModulesForExtensions()
                .stream()
                .map(Throwing.<ClassName, Module>function(className -> instantiateNoChildModule(injector, className)))
                .peek(module -> LOGGER.info("Enabling injects contained in " + module.getClass().getCanonicalName()))
                .collect(ImmutableList.toImmutableList()));
        this.injector = injector.createChildInjector(additionalExtensionBindings);
    }

    private <T> T instantiateNoChildModule(Injector injector, ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY)
                .instantiate(className);
    }

    @Override
    public <T> T instantiate(ClassName className) throws ClassNotFoundException {
        return new InvocationPerformer<T>(injector, extendedClassLoader, NamingScheme.IDENTITY)
                .instantiate(className);
    }

    @Override
    public <T> InvocationPerformer<T> withNamingSheme(NamingScheme namingSheme) {
        return new InvocationPerformer<>(injector, extendedClassLoader, namingSheme);
    }

    @Override
    public <T> InvocationPerformer<T> withChildModule(Module childModule) {
        return new InvocationPerformer<>(injector.createChildInjector(childModule), extendedClassLoader, NamingScheme.IDENTITY);
    }
}
