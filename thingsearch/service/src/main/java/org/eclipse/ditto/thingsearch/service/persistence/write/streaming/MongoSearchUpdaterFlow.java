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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SubSource;

/**
 * Flow mapping write models to write results via the search persistence.
 */
final class MongoSearchUpdaterFlow {

    private static final String TRACE_THING_BULK_UPDATE = "things_search_thing_bulkUpdate";
    private static final String COUNT_THING_BULK_UPDATES_PER_BULK = "things_search_thing_bulkUpdate_updates_per_bulk";
    private static final String UPDATE_TYPE_TAG = "update_type";

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(MongoSearchUpdaterFlow.class);

    private final MongoCollection<BsonDocument> collection;
    private final MongoCollection<BsonDocument> collectionWithAcknowledgements;
    private final SearchUpdateMapper searchUpdateMapper;

    private MongoSearchUpdaterFlow(final MongoCollection<BsonDocument> collection,
            final PersistenceStreamConfig persistenceConfig,
            final SearchUpdateMapper searchUpdateMapper) {

        this.collection = collection;
        collectionWithAcknowledgements = collection.withWriteConcern(
                persistenceConfig.getWithAcknowledgementsWriteConcern());
        this.searchUpdateMapper = searchUpdateMapper;
    }

    /**
     * Create a MongoSearchUpdaterFlow object.
     *
     * @param database the MongoDB database.
     * @param persistenceConfig the persistence configuration for the search updater stream.
     * @param searchUpdateMapper the mapper for custom processing on search updates.
     * @return the MongoSearchUpdaterFlow object.
     */
    public static MongoSearchUpdaterFlow of(final MongoDatabase database,
            final PersistenceStreamConfig persistenceConfig,
            final SearchUpdateMapper searchUpdateMapper) {

        return new MongoSearchUpdaterFlow(
                database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME, BsonDocument.class),
                persistenceConfig,
                searchUpdateMapper);
    }

    /**
     * Create a new flow through the search persistence.
     * No logging or recovery is attempted.
     *
     * @param subSource sub-source of write models.
     * @param shouldAcknowledge whether to use a write concern to guarantee the consistency of acknowledgements.
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#SEARCH_PERSISTED} was required or not.
     * @return sub-source of write results.
     */
    public SubSource<WriteResultAndErrors, NotUsed> start(
            final SubSource<List<AbstractWriteModel>, NotUsed> subSource,
            final boolean shouldAcknowledge) {

        return subSource.map(MongoSearchUpdaterFlow::sortBySeqNr)
                .flatMapConcat(searchUpdateMapper::processWriteModels)
                .flatMapConcat(writeModels -> executeBulkWrite(shouldAcknowledge, writeModels));
    }

    private Source<WriteResultAndErrors, NotUsed> executeBulkWrite(final boolean shouldAcknowledge,
            final Collection<MongoWriteModel> writeModels) {

        final MongoCollection<BsonDocument> theCollection;
        if (shouldAcknowledge) {
            theCollection = collectionWithAcknowledgements;
        } else {
            theCollection = collection;
        }

        final String bulkWriteCorrelationId = UUID.randomUUID().toString();

        if (writeModels.isEmpty()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId).debug("Requested to make empty update");
            return Source.empty();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .debug("Executing BulkWrite containing <{}> things: [<thingId>:{correlationIds}:<filter>]: {}",
                            writeModels.size(),
                            writeModels.stream()
                                    .map(writeModelPair -> "<" + writeModelPair.getDitto().getMetadata().getThingId() +
                                            ">:" +
                                            writeModelPair.getDitto().getMetadata().getEventsCorrelationIds()
                                                    .stream()
                                                    .collect(Collectors.joining(",", "{", "}"))
                                            + ":<" + extractFilterBson(writeModelPair.getBson()) + ">"
                                    )
                                    .toList());

            // only log the complete MongoDB writeModels on "TRACE" as they get really big and almost crash the logging backend:
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .trace("Executing BulkWrite <{}>", writeModels);
        }
        final var bulkWriteTimer = startBulkWriteTimer(writeModels);
        final var bsons = writeModels.stream().map(MongoWriteModel::getBson).toList();
        return Source.fromPublisher(theCollection.bulkWrite(bsons, new BulkWriteOptions().ordered(false)))
                .map(bulkWriteResult -> WriteResultAndErrors.success(
                        writeModels, bulkWriteResult, bulkWriteCorrelationId))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<WriteResultAndErrors, NotUsed>>()
                        .match(MongoBulkWriteException.class, bulkWriteException ->
                                Source.single(WriteResultAndErrors.failure(
                                        writeModels, bulkWriteException, bulkWriteCorrelationId))
                        )
                        .matchAny(error ->
                                Source.single(WriteResultAndErrors.unexpectedError(
                                        writeModels, error, bulkWriteCorrelationId))
                        )
                        .build()
                )
                .map(resultAndErrors -> {
                    stopBulkWriteTimer(bulkWriteTimer);
                    writeModels.forEach(writeModel ->
                            ConsistencyLag.startS6Acknowledge(writeModel.getDitto().getMetadata())
                    );
                    return resultAndErrors;
                });
    }

    private static String extractFilterBson(final WriteModel<BsonDocument> writeModel) {
        if (writeModel instanceof UpdateManyModel) {
            return ((UpdateManyModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof UpdateOneModel) {
            return ((UpdateOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof ReplaceOneModel) {
            return ((ReplaceOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof DeleteOneModel) {
            return ((DeleteOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof DeleteManyModel) {
            return ((DeleteManyModel<BsonDocument>) writeModel).getFilter().toString();
        }
        return "no filter";
    }

    private static StartedTimer startBulkWriteTimer(final Collection<?> writeModels) {
        DittoMetrics.histogram(COUNT_THING_BULK_UPDATES_PER_BULK).record((long) writeModels.size());
        return DittoMetrics.timer(TRACE_THING_BULK_UPDATE).tag(UPDATE_TYPE_TAG, "bulkUpdate").start();
    }

    private static void stopBulkWriteTimer(final StartedTimer timer) {
        try {
            timer.stop();
        } catch (final IllegalStateException e) {
            // it is okay if the timer stopped already; simply return the result.
        }
    }

    /**
     * Sort a list of write models by sequence number so that for each thing ID, any earlier update is guaranteed to
     * be applied earlier than any later update.
     *
     * @param writeModels A list of write models in a batch update.
     * @return The sorted write models.
     */
    private static List<AbstractWriteModel> sortBySeqNr(final List<AbstractWriteModel> writeModels) {
        return writeModels.stream()
                .sorted(Comparator.comparing(writeModel -> writeModel.getMetadata().getThingRevision()))
                .toList();
    }

}
