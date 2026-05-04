package org.sainm.codeatlas.analyzers.source;

interface JasperRuntimeClassResolver {
    boolean isAvailable(String className);

    Class<?> loadClass(String className) throws ClassNotFoundException;
}
