/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.commands.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeleteConnectionResponse}.
 */
public final class DeleteConnectionResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(CommandResponse.JsonFields.TYPE, DeleteConnectionResponse.TYPE)
            .set(CommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(ConnectivityCommandResponse.JsonFields.JSON_CONNECTION_ID, TestConstants.ID.toString())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeleteConnectionResponse.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteConnectionResponse.class,
                areImmutable(),
                provided(ConnectionId.class).isAlsoImmutable());
    }

    @Test
    public void createInstanceWithNullConnectionId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DeleteConnectionResponse.of(null, DittoHeaders.empty()))
                .withMessage("The %s must not be null!", "connectionId")
                .withNoCause();
    }

    @Test
    public void fromJsonReturnsExpected() {
        final DeleteConnectionResponse expected =
                DeleteConnectionResponse.of(TestConstants.ID, DittoHeaders.empty());

        final DeleteConnectionResponse actual =
                DeleteConnectionResponse.fromJson(KNOWN_JSON, DittoHeaders.empty());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual =
                DeleteConnectionResponse.of(TestConstants.ID, DittoHeaders.empty()).toJson();

        assertThat(actual).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonFactory.emptyPointer();

        final DeleteConnectionResponse underTest =
                DeleteConnectionResponse.of(TestConstants.ID, DittoHeaders.empty());

        DittoJsonAssertions.assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }
}
