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

package ai.grakn.graql.internal.reasoner;

import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.MultiUnifier;
import ai.grakn.graql.admin.Unifier;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * <p>
 * Implementation of the {@link MultiUnifier} interface.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public class MultiUnifierImpl implements MultiUnifier{

    private final ImmutableSet<Unifier> multiUnifier;

    public MultiUnifierImpl(Set<Unifier> us){
        this.multiUnifier = ImmutableSet.copyOf(us);
    }
    public MultiUnifierImpl(Unifier u){
        this.multiUnifier = ImmutableSet.of(u);
    }

    /**
     * identity multiunifier
     */
    public MultiUnifierImpl(){
        this.multiUnifier = ImmutableSet.of(new UnifierImpl());
    }

    @SafeVarargs
    MultiUnifierImpl(ImmutableMultimap<Var, Var>... maps){
        this.multiUnifier = ImmutableSet.<Unifier>builder()
                .addAll(Stream.of(maps).map(UnifierImpl::new).iterator())
                .build();
    }

    @Override
    public Stream<Unifier> stream() {
        return multiUnifier.stream();
    }

    @Override
    public Iterator<Unifier> iterator() {
        return multiUnifier.iterator();
    }

    @Override
    public Unifier getUnifier() {
        return Iterables.getOnlyElement(multiUnifier);
    }

    @Override
    public Unifier getAny() {
        //TODO add a check it's a structural one
        UnmodifiableIterator<Unifier> iterator = multiUnifier.iterator();
        if (!iterator.hasNext()){
            throw GraqlQueryException.nonExistentUnifier();
        }
        return iterator.next();
    }

    @Override
    public ImmutableSet<Unifier> unifiers() { return multiUnifier;}

    @Override
    public boolean isEmpty() {
        return multiUnifier.isEmpty();
    }

    @Override
    public boolean contains(Unifier u2) {
        return unifiers().stream().filter(u -> u.containsAll(u2)).findFirst().isPresent();
    }

    @Override
    public boolean containsAll(MultiUnifier mu) {
        return mu.unifiers().stream()
                .filter(u -> !unifiers().stream().filter(u::containsAll).findFirst().isPresent())
                .findFirst().isPresent();
    }

    @Override
    public MultiUnifier merge(Unifier u) {
        return new MultiUnifierImpl(
                multiUnifier.stream()
                        .map(unifier -> unifier.merge(u))
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public MultiUnifier inverse() {
        return new MultiUnifierImpl(
                multiUnifier.stream()
                        .map(Unifier::inverse)
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public int size() {
        return multiUnifier.size();
    }
}
