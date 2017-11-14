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

package ai.grakn.graql.internal.pattern.property;

import ai.grakn.concept.Label;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.test.rule.SampleKBContext;
import ai.grakn.util.Schema;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static ai.grakn.graql.Graql.var;

/**
 * @author Felix Chapman
 */
public class HasAttributePropertyTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @ClassRule
    public static final SampleKBContext sampleKB = SampleKBContext.empty();

    @Test
    public void whenCallingCheckValidPropertyAndLabelRefersToARole_Throw() {
        Label label = Schema.MetaSchema.ROLE.getLabel();
        HasAttributeProperty property = HasAttributeProperty.of(label, var("x").admin(), var().admin());

        exception.expect(GraqlQueryException.class);
        exception.expectMessage(GraqlQueryException.mustBeAttributeType(label).getMessage());

        property.checkValidProperty(sampleKB.tx(), var("y").admin());
    }
}