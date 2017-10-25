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

package ai.grakn;

import ai.grakn.exception.GraknTxOperationException;
import com.google.auto.value.AutoValue;

import javax.annotation.CheckReturnValue;
import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * <p>
 *     A {@link Keyspace}
 * </p>
 *
 * <p>
 *     A class which represents the unique name of a Grakn Knowledge Base
 * </p>
 *
 * @author fppt
 */
@AutoValue
public abstract class Keyspace implements Comparable<Keyspace>, Serializable {
    private static final int MAX_LENGTH = 48;
    private static final long serialVersionUID = 2726154016735929123L;

    public abstract String getValue();

    @Override
    public int compareTo(Keyspace o) {
        return getValue().compareTo(o.getValue());
    }

    /**
     *
     * @param value The string which potentially represents a unique {@link Keyspace}
     * @return The matching {@link Keyspace}
     */
    @CheckReturnValue
    public static Keyspace of(String value){
        if(!Pattern.matches("[a-z_][a-z_0-9]*", value) || value.length() > MAX_LENGTH) {
            throw GraknTxOperationException.invalidKeyspace(value);
        }
        return new AutoValue_Keyspace(value);
    }

    @Override
    public final String toString() {
        return getValue();
    }
}
