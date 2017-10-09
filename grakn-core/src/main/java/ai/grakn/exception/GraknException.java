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

package ai.grakn.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * <p>
 *     Root Grakn Exception
 * </p>
 *
 * <p>
 *     Encapsulates any exception which is thrown by the Grakn stack.
 *     This includes failures server side, failed graph mutations, and failed querying attempts
 * </p>
 *
 * @author fppt
 */
public class GraknException extends RuntimeException {
    // This is a set to mirror the format returned in Jersey,
    // which potentially contains more than one error
    @JsonProperty
    private Set<String> errors;

    public GraknException(String error){
        super(error);
        this.errors = ImmutableSet.of(error);
    }

    protected GraknException(String error, Exception e){
        super(error, e);
        this.errors = ImmutableSet.of(error);
    }

    @JsonProperty
    public Set<String> getException() {
        return errors;
    }
}
