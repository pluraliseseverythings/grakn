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

package ai.grakn.graql.internal.gremlin;

import ai.grakn.GraknTx;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Label;
import ai.grakn.concept.Type;
import ai.grakn.graql.Graql;
import ai.grakn.graql.Pattern;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Conjunction;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.graql.internal.gremlin.fragment.Fragment;
import ai.grakn.graql.internal.gremlin.fragment.Fragments;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.Stream;

import static ai.grakn.graql.Graql.and;
import static ai.grakn.graql.Graql.eq;
import static ai.grakn.graql.Graql.gt;
import static ai.grakn.graql.internal.gremlin.GraqlMatchers.feature;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConjunctionQueryTest {
    private Label resourceTypeWithoutSubTypesLabel = Label.of("name");
    private Label resourceTypeWithSubTypesLabel = Label.of("resource");
    private VarPattern resourceTypeWithoutSubTypes = Graql.label(resourceTypeWithoutSubTypesLabel);
    private VarPattern resourceTypeWithSubTypes = Graql.label(resourceTypeWithSubTypesLabel);
    private String literalValue = "Bob";
    private GraknTx tx;
    private Var x = Graql.var("x");
    private Var y = Graql.var("y");

    @SuppressWarnings("ResultOfMethodCallIgnored") // Mockito confuses IntelliJ
    @Before
    public void setUp() {
        tx = mock(GraknTx.class);

        Type resourceTypeWithoutSubTypesMock = mock(Type.class);
        doAnswer((answer) -> Stream.of(resourceTypeWithoutSubTypesMock)).when(resourceTypeWithoutSubTypesMock).subs();
        when(resourceTypeWithoutSubTypesMock.getLabel()).thenReturn(resourceTypeWithoutSubTypesLabel);

        Type resourceTypeWithSubTypesMock = mock(Type.class);
        doAnswer((answer) -> Stream.of(resourceTypeWithoutSubTypesMock, resourceTypeWithSubTypesMock))
                .when(resourceTypeWithSubTypesMock).subs();
        when(resourceTypeWithSubTypesMock.getLabel()).thenReturn(resourceTypeWithSubTypesLabel);

        when(tx.getSchemaConcept(resourceTypeWithoutSubTypesLabel)).thenReturn(resourceTypeWithoutSubTypesMock);
        when(tx.getSchemaConcept(resourceTypeWithSubTypesLabel)).thenReturn(resourceTypeWithSubTypesMock);
    }

    @Test
    public void whenVarRefersToATypeWithoutSubTypesAndALiteralValue_UseResourceIndex() {
        assertThat(x.isa(resourceTypeWithoutSubTypes).val(literalValue), usesResourceIndex());
    }

    @Test
    public void whenVarHasTwoResources_UseResourceIndexForBoth() {
        Pattern pattern = and(
                x.isa(resourceTypeWithoutSubTypes).val("Foo"),
                y.isa(resourceTypeWithoutSubTypes).val("Bar")
        );

        assertThat(pattern, allOf(usesResourceIndex(x, "Foo"), usesResourceIndex(y, "Bar")));
    }

    @Test
    public void whenVarRefersToATypeWithAnExplicitVarName_UseResourceIndex() {
        assertThat(x.isa(y.label(resourceTypeWithoutSubTypesLabel)).val(literalValue), usesResourceIndex());
    }

    @Test
    public void whenQueryUsesHasSyntax_UseResourceIndex() {
        assertThat(
                x.has(resourceTypeWithoutSubTypesLabel, y.val(literalValue)),
                usesResourceIndex(y, literalValue)
        );
    }

    @Test
    public void whenVarCanUseResourceIndexAndHasOtherProperties_UseResourceIndex() {
        assertThat(
                x.isa(resourceTypeWithoutSubTypes).val(literalValue).id(ConceptId.of("123")),
                usesResourceIndex()
        );
    }

    @Test
    public void whenVarCanUseResourceIndexAndThereIsAnotherVarThatCannot_UseResourceIndex() {
        assertThat(
                and(x.isa(resourceTypeWithoutSubTypes).val(literalValue), y.val(literalValue)),
                usesResourceIndex()
        );

        assertThat(
                and(y.isa(resourceTypeWithoutSubTypes).val(literalValue), x.val(literalValue)),
                usesResourceIndex(y, literalValue)
        );
    }

    @Test
    public void whenVarRefersToATypeWithSubtypes_DoNotUseResourceIndex() {
        assertThat(x.isa(resourceTypeWithSubTypes).val(literalValue), not(usesResourceIndex()));
    }

    @Test
    public void whenVarHasAValueComparator_DoNotUseResourceIndex() {
        assertThat(x.isa(resourceTypeWithoutSubTypes).val(gt(literalValue)), not(usesResourceIndex()));
    }

    @Test
    public void whenVarDoesNotHaveAType_DoNotUseResourceIndex() {
        assertThat(x.val(literalValue), not(usesResourceIndex()));
    }

    @Test
    public void whenVarDoesNotHaveAValue_DoNotUseResourceIndex() {
        assertThat(x.val(resourceTypeWithoutSubTypes), not(usesResourceIndex()));
    }

    @Test
    public void whenVarHasAValuePredicateThatRefersToAVar_DoNotUseResourceIndex() {
        assertThat(x.isa(resourceTypeWithoutSubTypes).val(eq(y)), not(usesResourceIndex(x, y)));
    }

    private Matcher<Pattern> usesResourceIndex() {
        return usesResourceIndex(x, literalValue);
    }

    private Matcher<Pattern> usesResourceIndex(Var varName, Object value) {
        Fragment resourceIndexFragment = Fragments.attributeIndex(null, varName, resourceTypeWithoutSubTypesLabel, value);

        return feature(hasItem(contains(resourceIndexFragment)), "fragment sets", pattern -> {
            Conjunction<VarPatternAdmin> conjunction = pattern.admin().getDisjunctiveNormalForm().getPatterns().iterator().next();
            return new ConjunctionQuery(conjunction, tx).getEquivalentFragmentSets();
        });
    }
}