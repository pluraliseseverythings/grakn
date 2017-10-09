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

package ai.grakn.util;

import ai.grakn.concept.Attribute;
import ai.grakn.concept.AttributeType;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.LabelId;
import ai.grakn.concept.Relationship;
import ai.grakn.concept.RelationshipType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Rule;
import ai.grakn.concept.SchemaConcept;
import ai.grakn.concept.Type;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import javax.annotation.CheckReturnValue;

import static ai.grakn.util.ErrorMessage.INVALID_IMPLICIT_TYPE;

/**
 * A type enum which restricts the types of links/concepts which can be created
 *
 * @author Filipe Teixeira
 */
public final class Schema {
    public final static String PREFIX_VERTEX = "V";
    public final static String PREFIX_EDGE = "E";

    private Schema() {
        throw new UnsupportedOperationException();
    }

    /**
     * The different types of edges between vertices
     */
    public enum EdgeLabel {
        ISA("isa"),
        SUB("sub"),
        RELATES("relates"),
        PLAYS("plays"),
        HYPOTHESIS("hypothesis"),
        CONCLUSION("conclusion"),
        ROLE_PLAYER("role-player"),
        ATTRIBUTE("attribute"),
        SHARD("shard");

        private final String label;

        EdgeLabel(String l) {
            label = l;
        }

        @CheckReturnValue
        public String getLabel() {
            return label;
        }
    }

    /**
     * The concepts which represent our internal schema
     */
    public enum MetaSchema {
        THING("thing", 1),
        ENTITY("entity", 2),
        ROLE("role", 3),
        ATTRIBUTE("attribute", 4),
        RELATIONSHIP("relationship", 5),
        RULE("rule", 6);


        private final Label label;
        private final LabelId id;

        MetaSchema(String s, int i) {
            label = Label.of(s);
            id = LabelId.of(i);
        }

        @CheckReturnValue
        public Label getLabel() {
            return label;
        }

        @CheckReturnValue
        public LabelId getId(){
            return id;
        }

        @CheckReturnValue
        public static boolean isMetaLabel(Label label) {
            for (MetaSchema metaSchema : MetaSchema.values()) {
                if (metaSchema.getLabel().equals(label)) return true;
            }
            return false;
        }
    }

    /**
     * Base Types reflecting the possible objects in the concept
     */
    public enum BaseType {
        //Schema Concepts
        SCHEMA_CONCEPT(SchemaConcept.class),
        TYPE(Type.class),
        ROLE(Role.class),
        RELATIONSHIP_TYPE(RelationshipType.class),
        ATTRIBUTE_TYPE(AttributeType.class),
        ENTITY_TYPE(EntityType.class),
        RULE(Rule.class),

        //Instances
        RELATIONSHIP(Relationship.class),
        ENTITY(Entity.class),
        ATTRIBUTE(Attribute.class),

        //Internal
        SHARD(Vertex.class);

        private final Class classType;

        BaseType(Class classType){
            this.classType = classType;
        }

        @CheckReturnValue
        public Class getClassType(){
            return classType;
        }
    }

    /**
     * An enum which defines the non-unique mutable properties of the concept.
     */
    public enum VertexProperty {
        //Unique Properties
        SCHEMA_LABEL(String.class), INDEX(String.class), ID(String.class), LABEL_ID(Integer.class),

        //Other Properties
        THING_TYPE_LABEL_ID(Integer.class), IS_ABSTRACT(Boolean.class), IS_IMPLICIT(Boolean.class),
        REGEX(String.class), DATA_TYPE(String.class), SHARD_COUNT(Long.class), CURRENT_LABEL_ID(Integer.class),
        RULE_WHEN(String.class), RULE_THEN(String.class), CURRENT_SHARD(String.class),

        //Supported Data Types
        VALUE_STRING(String.class), VALUE_LONG(Long.class),
        VALUE_DOUBLE(Double.class), VALUE_BOOLEAN(Boolean.class),
        VALUE_INTEGER(Integer.class), VALUE_FLOAT(Float.class),
        VALUE_DATE(Long.class);

        private final Class dataType;

        VertexProperty(Class dataType) {
            this.dataType = dataType;
        }

        @CheckReturnValue
        public Class getDataType() {
            return dataType;
        }
    }

    /**
     * A property enum defining the possible labels that can go on the edge label.
     */
    public enum EdgeProperty {
        RELATIONSHIP_ROLE_OWNER_LABEL_ID(Integer.class),
        RELATIONSHIP_ROLE_VALUE_LABEL_ID(Integer.class),
        ROLE_LABEL_ID(Integer.class),
        RELATIONSHIP_TYPE_LABEL_ID(Integer.class),
        REQUIRED(Boolean.class);

        private final Class dataType;

        EdgeProperty(Class dataType) {
            this.dataType = dataType;
        }

        @CheckReturnValue
        public Class getDataType() {
            return dataType;
        }
    }

    /**
     * This stores the schema which is required when implicitly creating roles for the has-{@link Attribute} methods
     */
    public enum ImplicitType {
        /**
         * Reserved character used by all implicit {@link Type}s
         */
        RESERVED("@"),

        /**
         * The label of the generic has-{@link Attribute} relationship, used for attaching {@link Attribute}s to instances with the 'has' syntax
         */
        HAS("@has-%s"),

        /**
         * The label of a role in has-{@link Attribute}, played by the owner of the {@link Attribute}
         */
        HAS_OWNER("@has-%s-owner"),

        /**
         * The label of a role in has-{@link Attribute}, played by the {@link Attribute}
         */
        HAS_VALUE("@has-%s-value"),

        /**
         * The label of the generic key relationship, used for attaching {@link Attribute}s to instances with the 'has' syntax and additionally constraining them to be unique
         */
        KEY("@key-%s"),

        /**
         * The label of a role in key, played by the owner of the key
         */
        KEY_OWNER("@key-%s-owner"),

        /**
         * The label of a role in key, played by the {@link Attribute}
         */
        KEY_VALUE("@key-%s-value");

        private final String label;

        ImplicitType(String label) {
            this.label = label;
        }

        @CheckReturnValue
        public Label getLabel(Label attributeType) {
            return attributeType.map(attribute -> String.format(label, attribute));
        }

        @CheckReturnValue
        public Label getLabel(String attributeType) {
            return Label.of(String.format(label, attributeType));
        }

        @CheckReturnValue
        public String getValue(){
            return label;
        }

        /**
         * Helper method which converts the implicit type label back into the original label from which is was built.
         *
         * @param implicitType the implicit type label
         * @return The original label which was used to build this type
         */
        @CheckReturnValue
        public static Label explicitLabel(Label implicitType){
            if(!implicitType.getValue().startsWith("key") && implicitType.getValue().startsWith("has")){
                throw new IllegalArgumentException(INVALID_IMPLICIT_TYPE.getMessage(implicitType));
            }

            int endIndex = implicitType.getValue().length();
            if(implicitType.getValue().endsWith("-value") || implicitType.getValue().endsWith("-owner")) {
                endIndex = implicitType.getValue().lastIndexOf("-");
            }

            return Label.of(implicitType.getValue().substring(4, endIndex));
        }
    }

    /**
     * An enum representing analytics schema elements
     */
    public enum Analytics {

        DEGREE("degree"),
        CLUSTER("cluster");

        private final String label;

        Analytics(String label) {
            this.label = label;
        }

        @CheckReturnValue
        public Label getLabel() {
            return Label.of(label);
        }
    }

    /**
     *
     * @param label The {@link AttributeType} label
     * @param value The value of the {@link Attribute}
     * @return A unique id for the {@link Attribute}
     */
    @CheckReturnValue
    public static String generateAttributeIndex(Label label, String value){
        return Schema.BaseType.ATTRIBUTE.name() + "-" + label + "-" + value;
    }
}
