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
 *
 */
package ai.grakn.engine.util;

import com.auth0.jwt.internal.org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 *     Engine utilities
 * </p>
 *
 * @author Domenico Corapi
 */
public class EngineUtil {
    public static String readBody(HttpServletRequest request) {
        StringWriter writer = new StringWriter();
        try {
            IOUtils.copy(request.getInputStream(), writer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read input stream");
        }
        return writer.toString();
    }

}
