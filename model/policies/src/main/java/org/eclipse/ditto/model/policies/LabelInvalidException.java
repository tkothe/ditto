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
package org.eclipse.ditto.model.policies;

import java.net.URI;
import java.text.MessageFormat;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeExceptionBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Thrown if a Policy Label is not valid.
 */
@Immutable
public final class LabelInvalidException extends DittoRuntimeException implements PolicyException {

    /**
     * Error code of this exception.
     */
    public static final String ERROR_CODE = ERROR_CODE_PREFIX + "label.invalid";

    private static final String MESSAGE_TEMPLATE = "Label ''{0}'' is not valid!";

    private static final String DEFAULT_DESCRIPTION =
            "It must not start with the blacklisted '" + ImmutableLabel.BLACKLISTED_IMPORTED_PREFIX + "' prefix.";

    private static final long serialVersionUID = -7013725864335663680L;

    /**
     * Constructs a new {@code LabelInvalidException} object.
     *
     * @param label the invalid Label.
     */
    public LabelInvalidException(final CharSequence label) {
        this(DittoHeaders.empty(), MessageFormat.format(MESSAGE_TEMPLATE, label), DEFAULT_DESCRIPTION, null, null);
    }

    private LabelInvalidException(final DittoHeaders dittoHeaders,
            @Nullable final String message,
            @Nullable final String description,
            @Nullable final Throwable cause,
            @Nullable final URI href) {
        super(ERROR_CODE, HttpStatusCode.BAD_REQUEST, dittoHeaders, message, description, cause, href);
    }

    /**
     * A mutable builder for a {@code LabelInvalidException}.
     *
     * @param label the label.
     * @return the builder.
     */
    public static Builder newBuilder(@Nullable final CharSequence label) {
        return new Builder(label);
    }

    /**
     * Constructs a new {@code LabelInvalidException} object with the given exception message.
     *
     * @param message detail message. This message can be later retrieved by the {@link #getMessage()} method.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new LabelInvalidException.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    public static LabelInvalidException fromMessage(@Nullable final String message,
            final DittoHeaders dittoHeaders) {
        return new Builder()
                .message(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * Constructs a new {@code LabelInvalidException} object with the exception message extracted from the
     * given JSON object.
     *
     * @param jsonObject the JSON to read the {@link JsonFields#MESSAGE} field from.
     * @param dittoHeaders the headers of the command which resulted in this exception.
     * @return the new LabelInvalidException.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the {@code jsonObject} does not have the {@link
     * JsonFields#MESSAGE} field.
     */
    public static LabelInvalidException fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Builder()
                .dittoHeaders(dittoHeaders)
                .message(readMessage(jsonObject))
                .description(readDescription(jsonObject).orElse(DEFAULT_DESCRIPTION))
                .href(readHRef(jsonObject).orElse(null))
                .build();
    }

    @Override
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * A mutable builder with a fluent API for a {@link LabelInvalidException}.
     */
    @NotThreadSafe
    public static final class Builder extends DittoRuntimeExceptionBuilder<LabelInvalidException> {

        private Builder() {
            description(DEFAULT_DESCRIPTION);
        }

        private Builder(@Nullable final CharSequence subjectId) {
            this();
            message(MessageFormat.format(MESSAGE_TEMPLATE, subjectId));
        }

        @Override
        protected LabelInvalidException doBuild(final DittoHeaders dittoHeaders,
                @Nullable final String message,
                @Nullable final String description,
                @Nullable final Throwable cause,
                @Nullable final URI href) {
            return new LabelInvalidException(dittoHeaders, message, description, cause, href);
        }

    }

}