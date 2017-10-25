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

package ai.grakn.util;

import com.google.common.base.Preconditions;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This Util class just takes care of going from host and port to string and viceversa
 * The URI class would require a schema
 *
 * @author pluraliseseverythings
 */
public class SimpleURI {

    private final int port;
    private final String host;

    public SimpleURI(String uri) {
        String[] uriSplit = uri.split(":");
        Preconditions.checkArgument(
                uriSplit.length == 2 || (uriSplit.length == 3 && uriSplit[1].contains("//")),
                "Malformed URI " + uri);
        // if it has the schema, we start parsing from after
        int bias = uriSplit.length == 3 ? 1 : 0;
        this.host = uriSplit[bias].replace("/", "").trim();
        this.port = Integer.parseInt(uriSplit[1 + bias].trim());
    }

    public SimpleURI(String host, int port) {
        this.port = port;
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    @Override
    public String toString() {
        return String.format("%s:%d", host, port);
    }

    public String toStringWithSchema() {
        return String.format("http://%s:%d", host, port);
    }

    public static SimpleURI withDefaultPort(String uri, int defaultPort) {
        if (uri.contains(":")) {
            return new SimpleURI(uri);
        } else {
            return new SimpleURI(uri, defaultPort);
        }
    }

    public URL toURL() {
        try {
            return new URL("http", getHost(), getPort(), "");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unexpected error while generating URL from " + this);
        }
    }
}
