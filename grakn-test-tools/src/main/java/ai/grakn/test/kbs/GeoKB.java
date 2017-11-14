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
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Thing;
import ai.grakn.graql.Pattern;
import ai.grakn.test.rule.SampleKBContext;

/**
 *
 * @author Kasper Piskorski
 *
 */
public class GeoKB extends TestKB {

    private static AttributeType<String> key;

    private static EntityType university, city, region, country, continent, geographicalObject;
    private static RelationshipType isLocatedIn;

    private static Role geoEntity, entityLocation;

    private static Thing Europe;
    private static Thing Warsaw, Wroclaw, London, Munich, Paris, Milan;
    private static Thing Masovia, Silesia, GreaterLondon, Bavaria, IleDeFrance, Lombardy;
    private static Thing Poland, England, Germany, France, Italy;
    private static Thing UW;
    private static Thing PW;
    private static Thing Imperial;
    private static Thing UCL;

    public static SampleKBContext context(){
        return new GeoKB().makeContext();
    }

    @Override
    public void buildSchema(GraknTx tx) {
        key = tx.putAttributeType("name", AttributeType.DataType.STRING);

        geoEntity = tx.putRole("geo-entity");
        entityLocation = tx.putRole("entity-location");
        isLocatedIn = tx.putRelationshipType("is-located-in")
                .relates(geoEntity).relates(entityLocation);

        geographicalObject = tx.putEntityType("geoObject")
                .plays(geoEntity)
                .plays(entityLocation);
        geographicalObject.attribute(key);

        continent = tx.putEntityType("continent")
                .sup(geographicalObject)
                .plays(entityLocation);
        country = tx.putEntityType("country")
                .sup(geographicalObject)
                .plays(geoEntity)
                .plays(entityLocation);
        region = tx.putEntityType("region")
                .sup(geographicalObject)
                .plays(geoEntity)
                .plays(entityLocation);
        city = tx.putEntityType("city")
                .sup(geographicalObject)
                .plays(geoEntity)
                .plays(entityLocation);
        university = tx.putEntityType("university")
                        .plays(geoEntity);
        university.attribute(key);
    }

    @Override
    public void buildInstances(GraknTx tx) {
        Europe = putEntity(tx, "Europe", continent, key.getLabel());

        Poland = putEntity(tx, "Poland", country, key.getLabel());
        Masovia = putEntity(tx, "Masovia", region, key.getLabel());
        Silesia = putEntity(tx, "Silesia", region, key.getLabel());
        Warsaw = putEntity(tx, "Warsaw", city, key.getLabel());
        Wroclaw = putEntity(tx, "Wroclaw", city, key.getLabel());
        UW = putEntity(tx, "University-of-Warsaw", university, key.getLabel());
        PW = putEntity(tx, "Warsaw-Polytechnics", university, key.getLabel());

        England = putEntity(tx, "England", country, key.getLabel());
        GreaterLondon = putEntity(tx, "GreaterLondon", region, key.getLabel());
        London = putEntity(tx, "London", city, key.getLabel());
        Imperial = putEntity(tx, "Imperial College London", university, key.getLabel());
        UCL = putEntity(tx, "University College London", university, key.getLabel());

        Germany = putEntity(tx, "Germany", country, key.getLabel());
        Bavaria = putEntity(tx, "Bavaria", region, key.getLabel());
        Munich = putEntity(tx, "Munich", city, key.getLabel());
        putEntity(tx, "University of Munich", university, key.getLabel());

        France = putEntity(tx, "France", country, key.getLabel());
        IleDeFrance = putEntity(tx, "IleDeFrance", region, key.getLabel());
        Paris = putEntity(tx, "Paris", city, key.getLabel());

        Italy = putEntity(tx, "Italy", country, key.getLabel());
        Lombardy = putEntity(tx, "Lombardy", region, key.getLabel());
        Milan = putEntity(tx, "Milan", city, key.getLabel());
    }

    @Override
    public void buildRelations(GraknTx tx) {

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Poland)
                .addRolePlayer(entityLocation, Europe);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Masovia)
                .addRolePlayer(entityLocation, Poland);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Warsaw)
                .addRolePlayer(entityLocation, Masovia);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, PW)
                .addRolePlayer(entityLocation, Warsaw);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, UW)
                .addRolePlayer(entityLocation, Warsaw);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Silesia)
                .addRolePlayer(entityLocation, Poland);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Wroclaw)
                .addRolePlayer(entityLocation, Silesia);



        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Imperial)
                .addRolePlayer(entityLocation, London);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, UCL)
                .addRolePlayer(entityLocation, London);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, London)
                .addRolePlayer(entityLocation, GreaterLondon);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, GreaterLondon)
                .addRolePlayer(entityLocation, England);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, England)
               .addRolePlayer(entityLocation, Europe);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Munich)
                .addRolePlayer(entityLocation, Bavaria);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Bavaria)
                .addRolePlayer(entityLocation, Germany);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Germany)
                .addRolePlayer(entityLocation, Europe);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Milan)
                .addRolePlayer(entityLocation, Lombardy);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Lombardy)
                .addRolePlayer(entityLocation, Italy);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Italy)
                .addRolePlayer(entityLocation, Europe);

        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, Paris)
                .addRolePlayer(entityLocation, IleDeFrance);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, IleDeFrance)
                .addRolePlayer(entityLocation, France);
        isLocatedIn.addRelationship()
                .addRolePlayer(geoEntity, France)
                .addRolePlayer(entityLocation, Europe);
    }

    @Override
    public void buildRules(GraknTx tx) {
        Pattern transitivity_LHS = tx.graql().parser().parsePattern("{(geo-entity: $x, entity-location: $y) isa is-located-in;" +
                "(geo-entity: $y, entity-location: $z) isa is-located-in;}");
        Pattern transitivity_RHS = tx.graql().parser().parsePattern("{(geo-entity: $x, entity-location: $z) isa is-located-in;}");
        tx.putRule("Geo Rule", transitivity_LHS, transitivity_RHS);
    }
}
