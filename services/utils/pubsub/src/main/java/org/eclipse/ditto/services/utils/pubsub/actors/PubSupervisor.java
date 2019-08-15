/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.pubsub.actors;

import javax.annotation.Nullable;

import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.TopicBloomFilters;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.pf.ReceiveBuilder;

/**
 * Supervisor of actors dealing with publications.
 * <pre>
 * {@code
 *                                PubSupervisor
 *                                      +
 *              supervises one-for-many |
 *             +------------------------+
 *             |                        |
 *             |                        |
 *             v                        v
 *    PubSubPublisher ----------> PubUpdater
 *         +          dead letters   +
 *         |          in case remote |
 *         |          member dies    |
 *         |                         |
 *         |                         |write local
 *         |                         |to be distributed later
 *         |read local               |
 *         |                         v
 *         +--------------------> DDataReplicator
 * }
 * </pre>
 */
public final class PubSupervisor extends AbstractPubSubSupervisor {

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final TopicBloomFilters topicBloomFilters;

    @Nullable private ActorRef publisher;

    private PubSupervisor(final PubSubConfig pubSubConfig, final TopicBloomFilters topicBloomFilters) {
        super(pubSubConfig);
        this.topicBloomFilters = topicBloomFilters;
    }

    /**
     * Create Props object for this actor.
     *
     * @param pubSubConfig the pub-sub config.
     * @param topicBloomFilters read-write access to the distributed topic Bloom filters.
     * @return the Props object.
     */
    public static Props props(final PubSubConfig pubSubConfig, final TopicBloomFilters topicBloomFilters) {
        return Props.create(PubSupervisor.class, pubSubConfig, topicBloomFilters);
    }

    @Override
    protected Receive createPubSubBehavior() {
        return ReceiveBuilder.create()
                .match(Publisher.Publish.class, this::isPublisherAvailable, this::publish)
                .match(Publisher.Publish.class, this::publisherUnavailable)
                .build();
    }

    @Override
    protected void onChildFailure() {
        publisher = null;
    }

    @Override
    protected void startChildren() {
        final ActorRef updater = startChild(PubUpdater.props(topicBloomFilters), PubUpdater.ACTOR_NAME_PREFIX);
        publisher = startChild(Publisher.props(getSeeds(), topicBloomFilters, updater), Publisher.ACTOR_NAME_PREFIX);
    }

    private boolean isPublisherAvailable() {
        return publisher != null;
    }

    @SuppressWarnings("ConstantConditions")
    private void publish(final Publisher.Publish publish) {
        publisher.tell(publish, getSender());
    }

    private void publisherUnavailable(final Publisher.Publish publish) {
        log.error("Publisher unavailable. Dropping <{}>", publish);
    }
}
