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

package ai.grakn.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import java.util.List;

/**
 * <p>
 * Class representing the body of a request for the Tasks endpoint
 * </p>
 *
 * @author Domenico Corapi
 */
@AutoValue
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = AutoValue_Tasks.Builder.class)
public abstract class Tasks {

    public static final String WAIT = "wait";
    public static final String TASKS = "tasks";

    @JsonProperty(WAIT)
    public abstract boolean isWait();
    @JsonProperty(TASKS)
    public abstract List<Task> getTasks();

    public static Builder builder() {
        return new AutoValue_Tasks.Builder();
    }

    /**
     * Builder
     *
     * @author Domenico Corapi
     */
    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty(WAIT)
        public abstract Builder setWait(boolean wait);
        @JsonProperty(TASKS)
        public abstract Builder setTasks(List<Task> tasks);

        public abstract Tasks build();
    }

}
