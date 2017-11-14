---
title: Insert Queries
keywords: graql, query, insert
last_updated: August 10, 2016
tags: [graql]
summary: "Graql Insert Queries"
sidebar: documentation_sidebar
permalink: /documentation/graql/insert-queries.html
folder: documentation
KB: genealogy-plus
---

The page documents use of the Graql `insert` query, which will insert a specified [variable pattern](./matches.html#variable-patterns)
describing data. To follow along, or experiment further, with the examples given below, please
load the *basic-genealogy.gql* file, which can be found in the *examples* directory of the Grakn installation zip, or on
[Github](https://github.com/graknlabs/grakn/blob/master/grakn-dist/src/examples/basic-genealogy.gql).

{% include note.html content="If you are working in the Graql shell, don't forget to `commit` to store an insertion in
the knowledge base." %}


## `match-insert`

If a [match](matches.html) is provided, the query will insert the given variable patterns for every result of the query.
The pattern describes [properties](#properties) to set on a particular concept and can optionally be bound to a variable or an ID.

In the example below, we insert additional (fictional) information for a `person` entity who we have matched through `identifier` Mary Guthrie.

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell1" data-toggle="tab">Graql</a></li>
    <li><a href="#java1" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell1">
<pre>
match $p has identifier "Mary Guthrie"; insert $p has middlename "Mathilda"; $p has birth-date "1902-01-01"; $p has death-date "1952-01-01"; $p has age 50;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java1">
<pre>
qb.match(var("p").has("identifier", "Mary Guthrie"))
    .insert(var("p").has("middlename", "Mathilda"), 
        var("p").has("birth-date", LocalDateTime.of(1902, 1, 1, 0, 0, 0).toString()),
        var("p").has("death-date", LocalDateTime.of(1952, 1, 1, 0, 0, 0).toString()),
        var("p").has("age", 50)
    ).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->


## Properties

### isa

Set the type of the inserted concept.

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell2" data-toggle="tab">Graql</a></li>
    <li><a href="#java2" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell2">
<pre>
insert has identifier "Titus Groan" isa person;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java2">
<pre>
qb.insert(var().has("identifier", "Titus Groan").isa("person")).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->


### id

It is not possible to insert a concept with the given id, as this is the job of the system. However, if you attempt to insert by id, you will retrieve a concept if one with that id already exists. The created or retrieved concept can then be modified with further properties.

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell3" data-toggle="tab">Graql</a></li>
    <li><a href="#java3" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell3">
<pre>
<!--test-ignore-->
insert id "1376496" isa person;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java3">
<pre>
<!--test-ignore-->
qb.insert(var().id(ConceptId.of("1376496")).isa("person")).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->


### val

Set the value of the concept.
<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell4" data-toggle="tab">Graql</a></li>
    <li><a href="#java4" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell4">
<pre>
insert val "Ash" isa surname;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java4">
<pre>
qb.insert(var().val("Ash").isa("surname")).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->

### has

Add an attribute of the given type to the concept.

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell5" data-toggle="tab">Graql</a></li>
    <li><a href="#java5" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell5">
<pre>
insert isa person, has identifier "Fuchsia Groan" has gender "female";
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java5">
<pre>
qb.insert(var().isa("person").has("identifier", "Fuchsia Groan").has("gender", "female")).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->

You can also specify a variable to represent the relationship connecting the thing and the attribute:

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell6" data-toggle="tab">Graql</a></li>
    <li><a href="#java6" data-toggle="tab">Java</a></li>
</ul>

<!-- TODO: Update to final syntax -->
<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell6">
<pre>
insert isa person has identifier "Fuchsia Groan" via $r;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java6">
<pre>
qb.insert(var().isa("person").has(Label.of("identifier"), var().val("Fuchsia Groan"), var("r"))).execute();
</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->

### relationship

Make the concept a relationship that relates the given role players, playing the given roles.
*(With apologies to 'Gormenghast' fans, who will be aware that Titus and Fuchsia are siblings and thus cannot marry).*

<ul id="profileTabs" class="nav nav-tabs">
    <li class="active"><a href="#shell7" data-toggle="tab">Graql</a></li>
    <li><a href="#java7" data-toggle="tab">Java</a></li>
</ul>

<div class="tab-content">
<div role="tabpanel" class="tab-pane active" id="shell7">
<pre>
match $p1 has identifier "Titus Groan"; $p2 has identifier "Fuchsia Groan"; insert (spouse: $p1, spouse: $p2) isa marriage;
</pre>
</div>
<div role="tabpanel" class="tab-pane" id="java7">
<pre>
qb.match(
  var("p1").has("name", "Titus Groan"),
  var("p2").has("name", "Fuchsia Groan")
).insert(
  var()
    .rel("spouse", "p1")
    .rel("spouse", "p2")
    .isa("marriage")
).execute();

</pre>
</div> <!-- tab-pane -->
</div> <!-- tab-content -->

{% include links.html %}
