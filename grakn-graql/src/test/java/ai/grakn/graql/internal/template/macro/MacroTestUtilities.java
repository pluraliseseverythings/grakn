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

package ai.grakn.graql.internal.template.macro;

import ai.grakn.graql.Graql;
import ai.grakn.graql.Query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.grakn.graql.Graql.parse;
import static junit.framework.TestCase.assertEquals;

public class MacroTestUtilities {

    public static void assertParseEquals(String template, Map<String, Object> data, String expected){
        List<Query> result = Graql.parser().parseTemplate(template, data).collect(Collectors.toList());
        assertEquals(parse(expected), result.get(0));
    }
}
