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

package ai.grakn.graql.internal.pattern;

import ai.grakn.graql.Var;
import ai.grakn.graql.admin.VarProperty;
import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;

import java.util.Set;

/**
 * Default implementation of {@link Var}.
 *
 * @author Felix Chapman
 */
@AutoValue
abstract class VarImpl extends AbstractVarPattern implements Var {

    @Override
    public boolean isUserDefinedName() {
        return kind() == Kind.UserDefined;
    }

    @Override
    public Var asUserDefined() {
        if (isUserDefinedName()) {
            return this;
        } else {
            return new AutoValue_VarImpl(getValue(), Kind.UserDefined);
        }
    }

    @Memoized
    @Override
    public String name() {
        return kind().prefix() + getValue();
    }

    @Override
    public String shortName() {
        return kind().prefix() + StringUtils.left(getValue(), 3);
    }

    @Override
    public Var var() {
        return this;
    }

    @Override
    protected Set<VarProperty> properties() {
        return ImmutableSet.of();
    }

    @Override
    public String toString() {
        return "$" + getValue();
    }

    @Override
    public final boolean equals(Object o) {
        // This equals implementation is special: it ignores whether a variable is user-defined
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VarImpl varName = (VarImpl) o;

        return getValue().equals(varName.getValue());
    }

    @Override
    public final int hashCode() {
        // This hashCode implementation is special: it ignores whether a variable is user-defined
        return getValue().hashCode();
     }
}
