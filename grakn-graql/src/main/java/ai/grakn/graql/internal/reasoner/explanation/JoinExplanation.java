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

package ai.grakn.graql.internal.reasoner.explanation;

import ai.grakn.graql.admin.Answer;
import ai.grakn.graql.admin.AnswerExplanation;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueries;
import ai.grakn.graql.internal.reasoner.query.ReasonerQueryImpl;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 * <p>
 * Explanation class for a join explanation - resulting from merging atoms in a conjunction.
 * </p>
 *
 * @author Kasper Piskorski
 *
 */
public class JoinExplanation extends Explanation {

    public JoinExplanation(){ super();}
    public JoinExplanation(Set<Answer> answers){ super(answers);}
    public JoinExplanation(ReasonerQueryImpl q, Answer mergedAnswer){
        super(q, q.selectAtoms().stream()
                .map(at -> at.inferTypes(mergedAnswer.project(at.getVarNames())))
                .map(ReasonerQueries::atomic)
                .map(aq -> mergedAnswer.project(aq.getVarNames()).explain(new LookupExplanation(aq)))
                .collect(Collectors.toSet())
        );
    }

    @Override
    public AnswerExplanation withAnswers(Set<Answer> answers) {
        return new JoinExplanation(answers);
    }

    @Override
    public boolean isJoinExplanation(){ return true;}
}
