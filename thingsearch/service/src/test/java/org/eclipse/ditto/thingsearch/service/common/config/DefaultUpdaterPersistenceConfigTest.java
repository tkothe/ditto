/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.ReadPreference;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultUpdaterPersistenceConfig}.
 */
public final class DefaultUpdaterPersistenceConfigTest {


    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("updater-persistence-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultUpdaterPersistenceConfig.class,
                areImmutable(),
                provided(ReadPreference.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultUpdaterPersistenceConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final UpdaterPersistenceConfig underTest = DefaultUpdaterPersistenceConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.readConcern())
                .as(UpdaterPersistenceConfig.ConfigValue.READ_CONCERN.getConfigPath())
                .isEqualTo(ReadConcern.ofReadConcern(
                        (String) UpdaterPersistenceConfig.ConfigValue.READ_CONCERN.getDefaultValue())
                        .orElseThrow());

        softly.assertThat(underTest.readPreference())
                .as(UpdaterPersistenceConfig.ConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.ofReadPreference(
                        (String) UpdaterPersistenceConfig.ConfigValue.READ_PREFERENCE.getDefaultValue())
                        .orElseThrow());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final UpdaterPersistenceConfig underTest = DefaultUpdaterPersistenceConfig.of(config);

        softly.assertThat(underTest.readConcern())
                .as(UpdaterPersistenceConfig.ConfigValue.READ_CONCERN.getConfigPath())
                .isEqualTo(ReadConcern.AVAILABLE);

        softly.assertThat(underTest.readPreference())
                .as(UpdaterPersistenceConfig.ConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.SECONDARY_PREFERRED);
    }

}
