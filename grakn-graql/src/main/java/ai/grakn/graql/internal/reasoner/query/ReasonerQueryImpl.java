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

package ai.grakn.graql.internal.reasoner.query;

import ai.grakn.GraknTx;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.GetQuery;
import ai.grakn.graql.Var;
import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.Atomic;
import ai.grakn.graql.admin.Conjunction;
import ai.grakn.graql.admin.MultiUnifier;
import ai.grakn.graql.admin.PatternAdmin;
import ai.grakn.graql.admin.ReasonerQuery;
import ai.grakn.graql.admin.Unifier;
import ai.grakn.graql.admin.UnifierComparison;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.graql.internal.pattern.Patterns;
import ai.grakn.graql.internal.query.QueryAnswer;
import ai.grakn.graql.internal.reasoner.MultiUnifierImpl;
import ai.grakn.graql.internal.reasoner.ResolutionIterator;
import ai.grakn.graql.internal.reasoner.ResolutionPlan;
import ai.grakn.graql.internal.reasoner.UnifierType;
import ai.grakn.graql.internal.reasoner.atom.Atom;
import ai.grakn.graql.internal.reasoner.atom.AtomicBase;
import ai.grakn.graql.internal.reasoner.atom.AtomicFactory;
import ai.grakn.graql.internal.reasoner.atom.binary.RelationshipAtom;
import ai.grakn.graql.internal.reasoner.atom.binary.type.IsaAtom;
import ai.grakn.graql.internal.reasoner.atom.predicate.IdPredicate;
import ai.grakn.graql.internal.reasoner.atom.predicate.NeqPredicate;
import ai.grakn.graql.internal.reasoner.cache.Cache;
import ai.grakn.graql.internal.reasoner.cache.LazyQueryCache;
import ai.grakn.graql.internal.reasoner.cache.QueryCache;
import ai.grakn.graql.internal.reasoner.explanation.JoinExplanation;
import ai.grakn.graql.internal.reasoner.rule.InferenceRule;
import ai.grakn.graql.internal.reasoner.rule.RuleUtils;
import ai.grakn.graql.internal.reasoner.state.AnswerState;
import ai.grakn.graql.internal.reasoner.state.ConjunctiveState;
import ai.grakn.graql.internal.reasoner.state.CumulativeState;
import ai.grakn.graql.internal.reasoner.state.QueryState;
import ai.grakn.graql.internal.reasoner.state.QueryStateBase;
import ai.grakn.graql.internal.reasoner.state.ResolutionState;
import ai.grakn.graql.internal.reasoner.utils.Pair;
import ai.grakn.util.Schema;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.LinkedList;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ai.grakn.graql.Graql.var;
import static ai.grakn.graql.internal.reasoner.query.QueryAnswerStream.join;
import static ai.grakn.graql.internal.reasoner.query.QueryAnswerStream.joinWithInverse;
import static ai.grakn.graql.internal.reasoner.query.QueryAnswerStream.nonEqualsFilter;

/**
 *
 * <p>
 * Base reasoner query providing resolution and atom handling facilities for conjunctive graql queries.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public class ReasonerQueryImpl implements ReasonerQuery {

    private final GraknTx tx;
    private final ImmutableSet<Atomic> atomSet;
    private Answer substitution = null;
    private ImmutableMap<Var, Type> varTypeMap = null;

    private static final Logger LOG = LoggerFactory.getLogger(ReasonerQueryImpl.class);

    ReasonerQueryImpl(Conjunction<VarPatternAdmin> pattern, GraknTx tx) {
        this.tx = tx;
        this.atomSet = ImmutableSet.<Atomic>builder()
                .addAll(AtomicFactory.createAtoms(pattern, this).iterator())
                .build();
    }

    ReasonerQueryImpl(Set<Atomic> atoms, GraknTx tx){
        this.tx = tx;
        this.atomSet = ImmutableSet.<Atomic>builder()
                .addAll(atoms.stream().map(at -> AtomicFactory.create(at, this)).iterator())
                .build();
    }

    ReasonerQueryImpl(List<Atom> atoms, GraknTx tx){
        this.tx = tx;
        this.atomSet =  ImmutableSet.<Atomic>builder()
                .addAll(atoms.stream()
                        .flatMap(at -> Stream.concat(Stream.of(at), at.getNonSelectableConstraints()))
                        .map(at -> AtomicFactory.create(at, this)).iterator())
                .build();
    }

    ReasonerQueryImpl(Atom atom) {
        this(Collections.singletonList(atom), atom.getParentQuery().tx());
    }

    ReasonerQueryImpl(ReasonerQueryImpl q) {
        this.tx = q.tx;
        this.atomSet =  ImmutableSet.<Atomic>builder()
                .addAll(q.getAtoms().stream().map(at -> AtomicFactory.create(at, this)).iterator())
                .build();
    }

    /**
     * @param sub substitution to be inserted into the query
     * @return corresponding query with additional substitution
     */
    public ReasonerQueryImpl withSubstitution(Answer sub){
        return new ReasonerQueryImpl(Sets.union(this.getAtoms(), sub.toPredicates(this)), this.tx());
    }

    /**
     * @return corresponding reasoner query with inferred types
     */
    public ReasonerQueryImpl inferTypes() {
        return new ReasonerQueryImpl(getAtoms().stream().map(Atomic::inferTypes).collect(Collectors.toSet()), tx());
    }

    /**
     * @return corresponding positive query (with neq predicates removed)
     */
    public ReasonerQueryImpl positive(){
        return new ReasonerQueryImpl(getAtoms().stream().filter(at -> !(at instanceof NeqPredicate)).collect(Collectors.toSet()), tx());
    }

    /**
     * @param transform map defining id transform: var -> new id
     * @return new query with id predicates transformed according to the transform
     */
    public ReasonerQueryImpl transformIds(Map<Var, ConceptId> transform){
        Set<Atomic> atoms = this.getAtoms(IdPredicate.class).map(p -> {
            ConceptId conceptId = transform.get(p.getVarName());
            if (conceptId != null) return new IdPredicate(p.getVarName(), conceptId, p.getParentQuery());
            return p;
        }).collect(Collectors.toSet());
        getAtoms().stream().filter(at -> !(at instanceof IdPredicate)).forEach(atoms::add);
        return new ReasonerQueryImpl(atoms, tx());
    }

    @Override
    public String toString(){
        return "{\n\t" +
                getAtoms(Atom.class)
                        .map(Atomic::toString)
                        .collect(Collectors.joining(";\n\t")) +
                "\n}\n";
    }

    @Override
    public ReasonerQuery copy() {
        return new ReasonerQueryImpl(this);
    }

    //alpha-equivalence equality
    @Override
    public boolean equals(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) return false;
        if (obj == this) return true;
        ReasonerQueryImpl q2 = (ReasonerQueryImpl) obj;
        return this.isEquivalent(q2);
    }

    @Override
    public int hashCode() {
        return QueryEquivalence.AlphaEquivalence.hash(this);
    }

    /**
     * @param q query to be compared with
     * @return true if two queries are alpha-equivalent
     */
    public boolean isEquivalent(ReasonerQueryImpl q) {
        return QueryEquivalence.AlphaEquivalence.equivalent(this, q);
    }

    /**
     * @param atom in question
     * @return true if query contains an equivalent atom
     */
    boolean containsEquivalentAtom(Atom atom, BiFunction<Atom, Atom, Boolean> equivalenceFunction) {
        return !getEquivalentAtoms(atom, equivalenceFunction).isEmpty();
    }

    private Set<Atom> getEquivalentAtoms(Atom atom, BiFunction<Atom, Atom, Boolean> equivalenceFunction) {
        return getAtoms(Atom.class)
                .filter(at -> equivalenceFunction.apply(at, atom))
                .collect(Collectors.toSet());
    }

    @Override
    public GraknTx tx() {
        return tx;
    }

    /**
     * @return conjunctive pattern composed of constituent atom patterns
     */
    public Conjunction<PatternAdmin> getBasePattern(){
        return Patterns.conjunction(
                getAtoms().stream()
                        .map(Atomic::getPattern)
                        .flatMap(p -> p.admin().varPatterns().stream())
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public Conjunction<PatternAdmin> getPattern() {
        return Patterns.conjunction(
                getAtoms().stream()
                        .map(Atomic::getCombinedPattern)
                        .flatMap(p -> p.admin().varPatterns().stream())
                        .collect(Collectors.toSet())
        );
    }

    @Override
    public Set<String> validateOntologically() {
        return getAtoms().stream()
                .flatMap(at -> at.validateOntologically().stream())
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isRuleResolvable() {
        return selectAtoms().stream().filter(Atom::isRuleResolvable).findFirst().isPresent();
    }

    private boolean isTransitive() {
        return getAtoms(Atom.class).filter(at -> this.containsEquivalentAtom(at, Atomic::isAlphaEquivalent)).count() == 2;
    }

    /**
     * @return true if this query is atomic
     */
    boolean isAtomic() {
        return atomSet.stream().filter(Atomic::isSelectable).count() == 1;
    }

    /**
     * @param typedVar variable of interest
     * @param parentType to be checked
     * @return true if typing the typeVar with type is compatible with role configuration of this query
     */
    @Override
    public boolean isTypeRoleCompatible(Var typedVar, Type parentType){
        if (parentType == null || Schema.MetaSchema.isMetaLabel(parentType.getLabel())) return true;

        Set<Type> parentTypes = parentType.subs().collect(Collectors.toSet());
        return !getAtoms(RelationshipAtom.class)
                .filter(ra -> ra.getVarNames().contains(typedVar))
                .filter(ra -> ra.getRoleVarMap().entries().stream()
                        //get roles this type needs to play
                        .filter(e -> e.getValue().equals(typedVar))
                        .filter(e -> !Schema.MetaSchema.isMetaLabel(e.getKey().getLabel()))
                        //check if it can play it
                        .filter(e -> !e.getKey().playedByTypes().filter(parentTypes::contains).findFirst().isPresent())
                        .findFirst().isPresent())
                .findFirst().isPresent();
    }

    @Override
    public Set<Atomic> getAtoms() { return atomSet;}

    @Override
    public <T extends Atomic> Stream<T> getAtoms(Class<T> type) {
        return getAtoms().stream().filter(type::isInstance).map(type::cast);}

    @Override
    public Set<Var> getVarNames() {
        Set<Var> vars = new HashSet<>();
        getAtoms().forEach(atom -> vars.addAll(atom.getVarNames()));
        return vars;
    }

    @Override
    public MultiUnifier getMultiUnifier(ReasonerQuery parent) {
        return getMultiUnifier(parent, UnifierType.EXACT);
    }

    /**
     * @param parent query for which unifier to unify with should be found
     * @param unifierType unifier type
     * @return corresponding multiunifier
     */
    public MultiUnifier getMultiUnifier(ReasonerQuery parent, UnifierComparison unifierType){
        throw GraqlQueryException.getUnifierOfNonAtomicQuery();
    }

    @Override
    public GetQuery getQuery() {
        return tx.graql().infer(false).match(getPattern()).get();
    }

    private Stream<IsaAtom> inferEntityTypes(Answer sub) {
        Set<Var> typedVars = getAtoms(IsaAtom.class).map(AtomicBase::getVarName).collect(Collectors.toSet());
        return Stream.concat(
                getAtoms(IdPredicate.class),
                sub.toPredicates(this).stream().map(IdPredicate.class::cast)
        )
                .filter(p -> !typedVars.contains(p.getVarName()))
                .map(p -> new Pair<>(p, tx().<Concept>getConcept(p.getPredicate())))
                .filter(p -> Objects.nonNull(p.getValue()))
                .filter(p -> p.getValue().isEntity())
                .map(p -> new IsaAtom(p.getKey().getVarName(), var(), p.getValue().asEntity().type(), this));
    }

    private Stream<IsaAtom> inferEntityTypes() {
        return inferEntityTypes(new QueryAnswer());
    }

    private Map<Var, Type> getVarTypeMap(Stream<IsaAtom> isas){
        HashMap<Var, Type> map = new HashMap<>();
        isas
                .map(at -> new Pair<>(at.getVarName(), at.getSchemaConcept()))
                .filter(p -> Objects.nonNull(p.getValue()))
                .filter(p -> p.getValue().isType())
                .forEach(p -> {
                    Var var = p.getKey();
                    Type newType = p.getValue().asType();
                    Type type = map.get(var);
                    if (type == null) map.put(var, newType);
                    else {
                        boolean isSubType = type.subs().filter(t -> t.equals(newType)).findFirst().isPresent();
                        if (isSubType) map.put(var, newType);
                    }
                });
        return map;
    }

    @Override
    public ImmutableMap<Var, Type> getVarTypeMap() {
        if (varTypeMap == null) {
            this.varTypeMap = ImmutableMap.copyOf(getVarTypeMap(
                    Stream.concat(
                        getAtoms(IsaAtom.class),
                        inferEntityTypes()
                    )
                )
            );
        }
        return varTypeMap;
    }

    @Override
    public ImmutableMap<Var, Type> getVarTypeMap(Answer sub) {
        return ImmutableMap.copyOf(getVarTypeMap(
                Stream.concat(
                        getAtoms(IsaAtom.class),
                        inferEntityTypes()
                )
                )
        );
    }

    /**
     * @param var variable name
     * @return id predicate for the specified var name if any
     */
    @Nullable
    public IdPredicate getIdPredicate(Var var) {
        return getAtoms(IdPredicate.class)
                .filter(sub -> sub.getVarName().equals(var))
                .findFirst().orElse(null);
    }

    /**
     * returns id transform that would convert this query to a query alpha-equivalent to the query,
     * provided they are structurally equivalent
     * @param query for which the transform is to be constructed
     * @param unifier between this query and provided query
     * @return id transform
     */
    public Map<Var, ConceptId> idTransform(ReasonerQueryImpl query, Unifier unifier){
        Map<Var, ConceptId> transform = new HashMap<>();
        this.getAtoms(IdPredicate.class)
                .forEach(thisP -> {
                    Collection<Var> vars = unifier.get(thisP.getVarName());
                    Var var = !vars.isEmpty()? Iterators.getOnlyElement(vars.iterator()) : thisP.getVarName();
                    IdPredicate p2 = query.getIdPredicate(var);
                    if ( p2 != null) transform.put(thisP.getVarName(), p2.getPredicate());
                });
        return transform;
    }

    /**
     * atom selection function
     * @return selected atoms
     */
    public Set<Atom> selectAtoms() {
        Set<Atom> atomsToSelect = getAtoms(Atom.class)
                .filter(Atomic::isSelectable)
                .collect(Collectors.toSet());
        if (atomsToSelect.size() <= 2) return atomsToSelect;

        Set<Atom> orderedSelection = new LinkedHashSet<>();

        Atom atom = atomsToSelect.stream()
                .filter(at -> at.getNeighbours(Atom.class).findFirst().isPresent())
                .findFirst().orElse(null);
        while(!atomsToSelect.isEmpty() && atom != null) {
            orderedSelection.add(atom);
            atomsToSelect.remove(atom);
            atom = atom.getNeighbours(Atom.class)
                    .filter(atomsToSelect::contains)
                    .findFirst().orElse(null);
        }
        //if disjoint select at random
        if (!atomsToSelect.isEmpty()) atomsToSelect.forEach(orderedSelection::add);

        if (orderedSelection.isEmpty()) {
            throw GraqlQueryException.noAtomsSelected(this);
        }
        return orderedSelection;
    }

    /**
     * @return substitution obtained from all id predicates (including internal) in the query
     */
    public Answer getSubstitution(){
        if (substitution == null) {
            Set<Var> varNames = getVarNames();
            Set<IdPredicate> predicates = getAtoms(IsaAtom.class)
                    .map(IsaAtom::getTypePredicate)
                    .filter(Objects::nonNull)
                    .filter(p -> varNames.contains(p.getVarName()))
                    .collect(Collectors.toSet());
            getAtoms(IdPredicate.class).forEach(predicates::add);

            // the mapping function is declared separately to please the Eclipse compiler
            Function<IdPredicate, Concept> f = p -> tx().getConcept(p.getPredicate());
            substitution = new QueryAnswer(predicates.stream().collect(Collectors.toMap(IdPredicate::getVarName, f)));
        }
        return substitution;
    }

    public Answer getRoleSubstitution(){
        Map<Var, Concept> roleSub = new HashMap<>();
        getAtoms(RelationshipAtom.class)
                .flatMap(RelationshipAtom::getRolePredicates)
                .forEach(p -> roleSub.put(p.getVarName(), tx().getConcept(p.getPredicate())));
        return new QueryAnswer(roleSub);
    }

    /**
     * @return true if this query is a ground query
     */
    boolean isGround(){
        return getSubstitution().vars().containsAll(getVarNames());
    }

    private Stream<Answer> fullJoin(Set<ReasonerAtomicQuery> subGoals,
                                    Cache<ReasonerAtomicQuery, ?> cache,
                                    Cache<ReasonerAtomicQuery, ?> dCache){
        List<ReasonerAtomicQuery> queries = selectAtoms().stream().map(ReasonerAtomicQuery::new).collect(Collectors.toList());
        Iterator<ReasonerAtomicQuery> qit = queries.iterator();
        ReasonerAtomicQuery childAtomicQuery = qit.next();
        Stream<Answer> join = childAtomicQuery.answerStream(subGoals, cache, dCache, false);
        Set<Var> joinedVars = childAtomicQuery.getVarNames();
        while(qit.hasNext()){
            childAtomicQuery = qit.next();
            Set<Var> joinVars = Sets.intersection(joinedVars, childAtomicQuery.getVarNames());
            Stream<Answer> localSubs = childAtomicQuery.answerStream(subGoals, cache, dCache, false);
            join = join(join, localSubs, ImmutableSet.copyOf(joinVars));
            joinedVars.addAll(childAtomicQuery.getVarNames());
        }
        return join;
    }

    private Stream<Answer> differentialJoin(Set<ReasonerAtomicQuery> subGoals,
                                            Cache<ReasonerAtomicQuery, ?> cache,
                                            Cache<ReasonerAtomicQuery, ?> dCache){
        Stream<Answer> join = Stream.empty();
        List<ReasonerAtomicQuery> queries = selectAtoms().stream().map(ReasonerAtomicQuery::new).collect(Collectors.toList());
        Set<ReasonerAtomicQuery> uniqueQueries = queries.stream().collect(Collectors.toSet());
        //only do one join for transitive queries
        List<ReasonerAtomicQuery> queriesToJoin  = isTransitive()? Lists.newArrayList(uniqueQueries) : queries;

        for(ReasonerAtomicQuery qi : queriesToJoin){
            Stream<Answer> subs = qi.answerStream(subGoals, cache, dCache, true);
            Set<Var> joinedVars = qi.getVarNames();
            for(ReasonerAtomicQuery qj : queries){
                if ( qj != qi ){
                    Set<Var> joinVars = Sets.intersection(joinedVars, qj.getVarNames());
                    subs = joinWithInverse(
                            subs,
                            cache.getAnswerStream(qj),
                            cache.getInverseAnswerMap(qj, joinVars),
                            ImmutableSet.copyOf(joinVars));
                    joinedVars.addAll(qj.getVarNames());
                }
            }
            join = Stream.concat(join, subs);
        }
        return join;
    }

    Stream<Answer> computeJoin(Set<ReasonerAtomicQuery> subGoals,
                               Cache<ReasonerAtomicQuery, ?> cache,
                               Cache<ReasonerAtomicQuery, ?> dCache,
                               boolean differentialJoin) {

        //handling neq predicates by using complement
        Set<NeqPredicate> neqPredicates = getAtoms(NeqPredicate.class).collect(Collectors.toSet());
        ReasonerQueryImpl positive = neqPredicates.isEmpty()? this : this.positive();

        Stream<Answer> join = differentialJoin?
                positive.differentialJoin(subGoals, cache, dCache) :
                positive.fullJoin(subGoals, cache, dCache);

        return join.filter(a -> nonEqualsFilter(a, neqPredicates));
    }

    @Override
    public Stream<Answer> resolve(boolean materialise) {
        if (materialise) {
            return resolveAndMaterialise(new LazyQueryCache<>(), new LazyQueryCache<>());
        } else {
            return new ResolutionIterator(this).hasStream();
        }
    }

    /**
     * resolves the query and materialises answers, explanations are not provided
     * @return stream of answers
     */
    Stream<Answer> resolveAndMaterialise(LazyQueryCache<ReasonerAtomicQuery> cache,
                                         LazyQueryCache<ReasonerAtomicQuery> dCache) {

        //handling neq predicates by using complement
        Set<NeqPredicate> neqPredicates = getAtoms(NeqPredicate.class).collect(Collectors.toSet());
        ReasonerQueryImpl positive = neqPredicates.isEmpty()? this : this.positive();

        Iterator<Atom> atIt = positive.selectAtoms().iterator();
        ReasonerAtomicQuery atomicQuery = new ReasonerAtomicQuery(atIt.next());
        Stream<Answer> answerStream = atomicQuery.resolveAndMaterialise(cache, dCache);
        Set<Var> joinedVars = atomicQuery.getVarNames();

        while (atIt.hasNext()) {
            atomicQuery = new ReasonerAtomicQuery(atIt.next());
            Stream<Answer> subAnswerStream = atomicQuery.resolveAndMaterialise(cache, dCache);
            Set<Var> joinVars = Sets.intersection(joinedVars, atomicQuery.getVarNames());
            answerStream = join(answerStream, subAnswerStream, ImmutableSet.copyOf(joinVars));
            joinedVars.addAll(atomicQuery.getVarNames());
        }

        Set<Var> vars = this.getVarNames();
        return answerStream
                .filter(a -> nonEqualsFilter(a, neqPredicates))
                .map(a -> a.project(vars));
    }

    /**
     * @param sub partial substitution
     * @param u unifier with parent state
     * @param parent parent state
     * @param subGoals set of visited sub goals
     * @param cache query cache
     * @return resolution subGoal formed from this query
     */
    public QueryState subGoal(Answer sub, Unifier u, QueryStateBase parent, Set<ReasonerAtomicQuery> subGoals, QueryCache<ReasonerAtomicQuery> cache){
        return new ConjunctiveState(this, sub, u, parent, subGoals, cache);
    }

    /**
     * @param sub partial substitution
     * @param u unifier with parent state
     * @param parent parent state
     * @param subGoals set of visited sub goals
     * @param cache query cache
     * @return resolution subGoals formed from this query obtained by expanding the inferred types contained in the query
     */
    public Stream<QueryState> subGoals(Answer sub, Unifier u, QueryStateBase parent, Set<ReasonerAtomicQuery> subGoals, QueryCache<ReasonerAtomicQuery> cache){
        return getQueryStream(sub)
                .map(q -> q.subGoal(sub, u, parent, subGoals, cache));
    }

    /**
     * @return stream of queries obtained by inserting all inferred possible types (if ambiguous)
     */
    Stream<ReasonerQueryImpl> getQueryStream(Answer sub){ return Stream.of(this);}

    /**
     * @param parent parent state
     * @param subGoals set of visited sub goals
     * @param cache query cache
     * @return query state iterator (db iter + unifier + state iter) for this query
     */
    public Pair<Iterator<ResolutionState>, MultiUnifier> queryStateIterator(QueryStateBase parent, Set<ReasonerAtomicQuery> subGoals, QueryCache<ReasonerAtomicQuery> cache){
        Iterator<AnswerState> dbIterator;
        Iterator<QueryStateBase> subGoalIterator;

        if(!this.isRuleResolvable()) {
            dbIterator = this.getQuery().stream()
                    .map(ans -> ans.explain(new JoinExplanation(this, ans)))
                    .map(ans -> new AnswerState(ans, parent.getUnifier(), parent))
                    .iterator();
            subGoalIterator = Collections.emptyIterator();
        } else {
            dbIterator = Collections.emptyIterator();
            LinkedList<ReasonerQueryImpl> subQueries = new ResolutionPlan(this).queryPlan();

            LOG.trace("CQ plan:\n" + subQueries.stream()
                    .map(sq -> sq.toString() + (sq.isRuleResolvable()? "*" : ""))
                    .collect(Collectors.joining("\n"))
            );

            subGoalIterator = Iterators.singletonIterator(new CumulativeState(subQueries, new QueryAnswer(), parent.getUnifier(), parent, subGoals, cache));
        }
        return new Pair<>(
                Iterators.concat(dbIterator, subGoalIterator),
                new MultiUnifierImpl()
        );
    }


    /**
     * reiteration might be required if rule graph contains loops with negative flux
     * or there exists a rule which head satisfies body
     * @return true if because of the rule graph form, the resolution of this query may require reiteration
     */
    public boolean requiresReiteration() {
        Set<InferenceRule> dependentRules = RuleUtils.getDependentRules(this);
        return RuleUtils.subGraphHasLoops(dependentRules, tx())
               || RuleUtils.subGraphHasRulesWithHeadSatisfyingBody(dependentRules);
    }
}
