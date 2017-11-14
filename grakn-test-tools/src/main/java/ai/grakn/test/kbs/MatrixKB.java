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

package ai.grakn.test.kbs;

import ai.grakn.GraknTx;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Label;
import ai.grakn.test.rule.SampleKBContext;
import ai.grakn.util.SampleKBLoader;

import java.util.function.Consumer;

/**
 *
 * @author Kasper Piskorski
 *
 */
public class MatrixKB extends TestKB {

    private final static Label key = Label.of("index");
    private final static String gqlFile = "matrix-test.gql";

    private final int n;
    private final int m;

    public MatrixKB(int n, int m){
        this.m = m;
        this.n = n;
    }

    public static SampleKBContext context(int n, int m) {
        return new MatrixKB(n, m).makeContext();
    }

    @Override
    public Consumer<GraknTx> build(){
        return (GraknTx graph) -> {
            SampleKBLoader.loadFromFile(graph, gqlFile);
            buildExtensionalDB(graph, n, m);
        };
    }

    private void buildExtensionalDB(GraknTx graph, int n, int m) {
        Role R1from = graph.getRole("R1-from");
        Role R1to = graph.getRole("R1-to");
        Role R2from = graph.getRole("R2-from");
        Role R2to = graph.getRole("R2-to");

        EntityType aEntity = graph.getEntityType("a-entity");
        EntityType bEntity = graph.getEntityType("b-entity");
        RelationshipType R1 = graph.getRelationshipType("R1");
        RelationshipType R2 = graph.getRelationshipType("R2");

        ConceptId[] aInstancesIds = new ConceptId[m+1];
        ConceptId[][] bInstancesIds = new ConceptId[m][n+1];
        aInstancesIds[0] = putEntity(graph, "a0", graph.getEntityType("start"), key).getId();
        aInstancesIds[m] = putEntity(graph, "a" + m, graph.getEntityType("end"), key).getId();
        for(int i = 1 ; i < m ;i++) {
            aInstancesIds[i] = putEntity(graph, "a" + i, aEntity, key).getId();
        }

        for(int i = 1 ; i < m ;i++) {
            for (int j = 1; j <= n; j++) {
                bInstancesIds[i][j] = putEntity(graph, "b" + i + j, bEntity, key).getId();
            }
        }

        for (int i = 0; i < m; i++) {
            R1.addRelationship()
                    .addRolePlayer(R1from, graph.getConcept(aInstancesIds[i]))
                    .addRolePlayer(R1to, graph.getConcept(aInstancesIds[i + 1]));
        }

        for(int j = 1 ; j <= n ;j++) {
            R2.addRelationship()
                    .addRolePlayer(R2from, graph.getConcept(aInstancesIds[0]))
                    .addRolePlayer(R2to, graph.getConcept(bInstancesIds[1][j]));
            R2.addRelationship()
                    .addRolePlayer(R2from, graph.getConcept(bInstancesIds[m-1][j]))
                    .addRolePlayer(R2to, graph.getConcept(aInstancesIds[m]));
            for (int i = 1; i < m - 1; i++) {
                R2.addRelationship()
                        .addRolePlayer(R2from, graph.getConcept(bInstancesIds[i][j]))
                        .addRolePlayer(R2to, graph.getConcept(bInstancesIds[i + 1][j]));
            }
        }
    }
}
