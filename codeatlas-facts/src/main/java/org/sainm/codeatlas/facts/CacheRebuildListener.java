package org.sainm.codeatlas.facts;

@FunctionalInterface
public interface CacheRebuildListener {
    void requestRebuild(CacheRebuildRequest request);
}
