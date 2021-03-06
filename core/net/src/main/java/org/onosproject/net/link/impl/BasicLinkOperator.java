/*
 * Copyright 2014-2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.link.impl;

import static org.slf4j.LoggerFactory.getLogger;

import java.time.Duration;

import org.onosproject.net.AnnotationKeys;
import org.onosproject.incubator.net.config.ConfigOperator;
import org.onosproject.incubator.net.config.basics.BasicLinkConfig;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Link;
import org.onosproject.net.SparseAnnotations;
import org.onosproject.net.link.DefaultLinkDescription;
import org.onosproject.net.link.LinkDescription;
import org.slf4j.Logger;

/**
 * Implementations of merge policies for various sources of link configuration
 * information. This includes applications, provides, and network configurations.
 */
public final class BasicLinkOperator implements ConfigOperator {

    private static final long DEF_BANDWIDTH = -1L;
    private static final Duration DEF_DURATION = Duration.ofNanos(-1L);
    private static final Logger log = getLogger(BasicLinkOperator.class);

    private BasicLinkOperator() {
    }

    /**
     * Generates a LinkDescription containing fields from a LinkDescription and
     * a LinkConfig.
     *
     * @param cfg the link config entity from network config
     * @param descr a LinkDescription
     * @return LinkDescription based on both sources
     */
    public static LinkDescription combine(BasicLinkConfig cfg, LinkDescription descr) {
        if (cfg == null) {
            return descr;
        }

        // cfg.type() defaults to DIRECT, so there is a risk of unwanted override.
        // do we want this behavior?
        Link.Type type = descr.type();
        if (cfg.type() != type) {
            type = cfg.type();
        }

        SparseAnnotations sa = combine(cfg, descr.annotations());
        return new DefaultLinkDescription(descr.src(), descr.dst(), type, sa);
    }

    /**
     * Generates an annotation from an existing annotation and LinkConfig.
     *
     * @param cfg the link config entity from network config
     * @param an the annotation
     * @return annotation combining both sources
     */
    public static SparseAnnotations combine(BasicLinkConfig cfg, SparseAnnotations an) {
        DefaultAnnotations.Builder b = DefaultAnnotations.builder();
        if (cfg.latency() != DEF_DURATION) {
            b.set(AnnotationKeys.LATENCY, cfg.latency().toString());
        }
        if (cfg.bandwidth() != DEF_BANDWIDTH) {
            b.set(AnnotationKeys.BANDWIDTH, String.valueOf(cfg.bandwidth()));
        }
        return DefaultAnnotations.union(an, b.build());
    }
}
