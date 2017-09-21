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
import java.util.Map;

/**
 * <p>
 * Class representing a Task in the REST API
 * </p>
 *
 * @author Domenico Corapi
 */
@AutoValue
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = AutoValue_Task.Builder.class)
public abstract class Task {
    public static final String CLASS_NAME = "className";
    public static final String CREATOR = "creator";
    public static final String RUN_AT = "runAt";
    public static final String LIMIT = "limit";
    public static final String CONFIGURATION = "configuration";


    @JsonProperty(CLASS_NAME)
    public abstract String getClassName();
    @JsonProperty(CREATOR)
    public abstract String getCreator();
    @JsonProperty(RUN_AT)
    public abstract long getRunAt();
    @JsonProperty(LIMIT)
    public abstract int getLimit();
    @JsonProperty(CONFIGURATION)
    public abstract Map<String, String> getConfiguration();

    public static Builder builder() {
        return new AutoValue_Task.Builder();
    }

    /**
     * Builder
     *
     * @author Domenico Corapi
     */
    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty(CLASS_NAME)
        public abstract Builder setClassName(String className);
        @JsonProperty(CREATOR)
        public abstract Builder setCreator(String creator);
        @JsonProperty(RUN_AT)
        public abstract Builder setRunAt(long runAt);
        @JsonProperty(LIMIT)
        public abstract Builder setLimit(int Limit);
        @JsonProperty(CONFIGURATION)
        public abstract Builder setConfiguration(Map<String, String> configuration);

        public abstract Task build();
    }
}
