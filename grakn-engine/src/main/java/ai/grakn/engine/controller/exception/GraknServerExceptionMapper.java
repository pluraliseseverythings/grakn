/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */
package ai.grakn.engine.controller.exception;

import ai.grakn.exception.GraknServerException;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.jersey.errors.ErrorMessage;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * <p>
 * Exception mapper
 * </p>
 *
 * @author Domenico Corapi
 */
public class GraknServerExceptionMapper implements ExceptionMapper<GraknServerException> {

    private final Meter exceptions;

    public GraknServerExceptionMapper(MetricRegistry metrics) {
        exceptions = metrics.meter(name(getClass(), "exceptions"));
    }

    @Override
    public Response toResponse(GraknServerException e) {
        exceptions.mark();
        return Response.status(e.getStatus())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new ErrorMessage(e.getStatus(), e.getMessage()))
                .build();
    }
}
