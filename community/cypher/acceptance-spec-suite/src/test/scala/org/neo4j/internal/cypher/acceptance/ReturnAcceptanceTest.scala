/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.internal.cypher.acceptance

import org.neo4j.cypher._
import org.neo4j.cypher.internal.compiler.v3_1.commands.expressions.PathImpl
import org.neo4j.cypher.internal.compiler.v3_1.test_helpers.CustomMatchers
import org.neo4j.graphdb._

class ReturnAcceptanceTest extends ExecutionEngineFunSuite with CustomMatchers with NewPlannerTestSupport {

  // TCK'd
  test("returning properties of deleted nodes should throw exception") {
    createNode("p" -> 0)

    val query = "MATCH (n) DELETE n RETURN n.p"

    an [EntityNotFoundException] should be thrownBy updateWithBothPlanners(query)
  }

  // TCK'd
  test("returning labels of deleted nodes should throw exception") {
    createLabeledNode("A")

    val query = "MATCH (n:A) DELETE n RETURN labels(n)"

    an [EntityNotFoundException] should be thrownBy updateWithBothPlanners(query)
  }

  // TCK'd
  test("returning properties of deleted relationships should throw exception") {
    relate(createNode(), createNode(), "T", Map("p" -> "a property"))

    val query = "MATCH ()-[r]->() DELETE r RETURN r.p"

    an [EntityNotFoundException] should be thrownBy updateWithBothPlanners(query)
  }

  // TCK'd
  test("returning the type of deleted relationships should throw exception") {
    relate(createNode(), createNode(), "T")

    val query = "MATCH ()-[r:T]->() DELETE r RETURN type(r)"

    an [EntityNotFoundException] should be thrownBy updateWithBothPlanners(query)
  }

  // TCK'd
  test("should accept a valid unicode literal") {
    val query = "RETURN '\\uf123' AS a"

    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode(query)

    result.toList should equal(List(Map("a" -> "\uF123")))
  }

  // TCK'd
  test("limit 0 should return an empty result") {
    createNode()
    createNode()
    createNode()
    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("match (n) return n limit 0")
    result should be(empty)
  }

  // TCK'd
  test("should not support sorting on things after distinct has removed it") {
    createNode("name" -> "A", "age" -> 13)
    createNode("name" -> "B", "age" -> 12)
    createNode("name" -> "C", "age" -> 11)

    intercept[SyntaxException](executeWithAllPlannersAndCompatibilityMode( """
match (a) where id(a) in [1,2,3,1]
return distinct a.name
order by a.age""").toList)

  }

  // TCK'd
  test("should not allow ordering on nodes") {
    createNode()
    createNode()

    intercept[IncomparableValuesException](executeWithAllPlannersAndCompatibilityMode("match (n) where id(n) in [0,1] return n order by n").toList)
  }

  // TCK'd
  test("expose problem with aliasing") {
    createNode("nisse")
    executeWithAllPlannersAndCompatibilityMode("match (n) return n.name, count(*) as foo order by n.name")
  }

  // TCK'd
  test("distinct on nullable values") {
    createNode("name" -> "Florescu")
    createNode()
    createNode()

    val result = executeWithAllPlannersAndCompatibilityMode("match (a) return distinct a.name").toList

    result should equal(List(Map("a.name" -> "Florescu"), Map("a.name" -> null)))
  }

  // TCK'd
  test("return all variables") {
    val a = createLabeledNode("Start")
    val b = createNode()
    val r = relate(a, b)

    val result = executeWithAllPlannersAndCompatibilityMode("match p=(a:Start)-->(b) return *").toList
    val first = result.head
    first.keys should equal(Set("a", "b", "p"))

    first("p") should equal(PathImpl(a, r, b))
  }

  // TCK'd
  test("issue 508") {
    createNode()

    val q = "match (n) set n.x=[1,2,3] return length(n.x)"

    updateWithBothPlannersAndCompatibilityMode(q).toList should equal(List(Map("length(n.x)" -> 3)))
  }

  test("long or double") {
    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("return 1, 1.5").toList.head

    result("1") should haveType[java.lang.Long]
    result("1.5") should haveType[java.lang.Double]
  }

  // TCK'd
  test("square function returns decimals") {
    val result = executeWithAllPlannersAndCompatibilityMode("return sqrt(12.96)").toList

    result should equal(List(Map("sqrt(12.96)" -> 3.6)))
  }

  // TCK'd
  test("maths inside aggregation") {
    val andres = createNode("name" -> "Andres")
    val michael = createNode("name" -> "Michael")
    val peter = createNode("name" -> "Peter")
    val bread = createNode("type" -> "Bread")
    val veg = createNode("type" -> "Veggies")
    val meat = createNode("type" -> "Meat")

    relate(andres, bread, "ATE", Map("times" -> 10))
    relate(andres, veg, "ATE", Map("times" -> 8))

    relate(michael, veg, "ATE", Map("times" -> 4))
    relate(michael, bread, "ATE", Map("times" -> 6))
    relate(michael, meat, "ATE", Map("times" -> 9))

    relate(peter, veg, "ATE", Map("times" -> 7))
    relate(peter, bread, "ATE", Map("times" -> 7))
    relate(peter, meat, "ATE", Map("times" -> 4))

    executeWithAllPlannersAndCompatibilityMode(
      """
    match (me)-[r1:ATE]->(food)<-[r2:ATE]-(you)
    where id(me) = 1

    with me,count(distinct r1) as H1,count(distinct r2) as H2,you
    match (me)-[r1:ATE]->(food)<-[r2:ATE]-(you)

    return me,you,sum((1-ABS(r1.times/H1-r2.times/H2))*(r1.times+r2.times)/(H1+H2))""").dumpToString()
  }

  // TCK'd
  test("should not be confused by rewriting about what is a relationship and what not") {
    val andres = createNode("name" -> "Andres")
    val michael = createNode("name" -> "Michael")
    val peter = createNode("name" -> "Peter")
    val bread = createNode("type" -> "Bread")
    val veg = createNode("type" -> "Veggies")
    val meat = createNode("type" -> "Meat")

    relate(andres, bread, "ATE", Map("times" -> 10))
    relate(andres, veg, "ATE", Map("times" -> 8))

    relate(michael, veg, "ATE", Map("times" -> 4))
    relate(michael, bread, "ATE", Map("times" -> 6))
    relate(michael, meat, "ATE", Map("times" -> 9))

    relate(peter, veg, "ATE", Map("times" -> 7))
    relate(peter, bread, "ATE", Map("times" -> 7))
    relate(peter, meat, "ATE", Map("times" -> 4))

    executeWithAllPlannersAndCompatibilityMode(
      """
    match (me)-[r1]->(you)

    with 1 AS x
    match (me)-[r1]->(food)<-[r2]-(you)

    return r1.times""").dumpToString()
  }

  test("should return shortest paths if using a ridiculously unhip cypher") {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a, b)
    relate(b, c)

    val result = eengine.execute("match (a), (c) where id(a) = 0 and id(c) = 2 return shortestPath((a)-[*]->(c))", Map.empty[String, Any], graph.session()).columnAs[Path]("shortestPath((a)-[*]->(c))").toList.head
    result.endNode() should equal(c)
    result.startNode() should equal(a)
    result.length() should equal(2)
  }

  test("should return shortest path") {
    val a = createLabeledNode("Start")
    val b = createNode()
    val c = createLabeledNode("End")
    relate(a, b)
    relate(b, c)

    val result = executeWithAllPlannersAndCompatibilityMode("match (a:Start), (c:End) return shortestPath((a)-[*]->(c))").columnAs[Path]("shortestPath((a)-[*]->(c))").toList.head
    result.endNode() should equal(c)
    result.startNode() should equal(a)
    result.length() should equal(2)
  }

  // TCK'd
  test("array prop output") {
    createNode("foo" -> Array(1, 2, 3))

    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("match (n) return n").dumpToString()

    result should include ("[1,2,3]")
  }

  // TCK'd
  test("map output") {
    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("return {a:1, b:'foo'}").dumpToString()

    result should ( include ("""{a -> 1, b -> "foo"}""") or include ("""{b -> "foo", a -> 1}""") )
  }

  // TCK'd
  test("should be able to return predicate result") {
    createNode()

    val result = executeWithAllPlannersAndCompatibilityMode("match (a) return id(a) = 0, a is null").toList

    result should equal(List(Map("id(a) = 0" -> true, "a is null" -> false)))
  }

  // TCK'd
  test("literal_collection") {
    val result = executeWithAllPlannersAndCompatibilityMode("return size([[],[]]+[[]]) as l").toList

    result should equal(List(Map("l" -> 3)))
  }

  // TCK'd
  test("array property should be accessible as list") {
    createNode()

    val result = updateWithBothPlannersAndCompatibilityMode("match (n) SET n.array = [1,2,3,4,5] RETURN tail(tail(n.array))").
      toList.
      head("tail(tail(n.array))").
      asInstanceOf[Iterable[_]]

    result.toList should equal(List(3, 4, 5))
  }

  // TCK'd
  test("getting top x when we have less than x left") {
    (0 to 15).foreach(x => createNode("count" -> x))

    val result = executeWithAllPlannersAndCompatibilityMode("MATCH (a) RETURN a.count ORDER BY a.count SKIP 10 LIMIT 10")

    result.toList should equal(List(
      Map("a.count" -> 10),
      Map("a.count" -> 11),
      Map("a.count" -> 12),
      Map("a.count" -> 13),
      Map("a.count" -> 14),
      Map("a.count" -> 15)
    ))
  }

  // TCK'd
  test("substring with default length") {
    val result = executeWithAllPlannersAndCompatibilityMode("return substring('0123456789', 1) as s")

    result.toList should equal(List(Map("s" -> "123456789")))
  }

  // TCK'd
  test("sort columns do not leak") {
    //GIVEN
    val result = executeWithAllPlannersAndCompatibilityMode("match (n) return * order by id(n)")

    //THEN
    result.columns should equal(List("n"))
  }

  // TCK'd
  test("should allow expression alias in order by with distinct") {
    createNode()

    //WHEN
    val result = executeWithAllPlannersAndCompatibilityMode(
      """MATCH (n)
        RETURN distinct ID(n) as id
        ORDER BY id DESC""")

    //THEN DOESN'T THROW EXCEPTION
    result.toList should equal(List(Map("id" -> 0)))
  }

  // TCK'd
  test("columns should not change when using order by and distinct") {
    val n = createNode()

    val result = executeWithAllPlannersAndCompatibilityMode("match (n) return distinct n order by id(n)")

    result.toList should equal(List(Map("n" -> n)))
  }

  // TCK'd
  test("should propagate null through math funcs") {
    val result = executeWithAllPlannersAndCompatibilityMode("return 1 + (2 - (3 * (4 / (5 ^ (6 % null))))) as A")

    result.toList should equal(List(Map("A" -> null)))
  }

  // TCK'd
  test("should be able to index into nested literal lists") {
    executeWithAllPlannersAndCompatibilityMode("RETURN [[1]][0][0]").toList
    // should not throw an exception
  }

  // TCK'd
  test("should be able to alias expressions") {
    createNode("id" -> 42)

    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("match (a) return a.id as a, a.id")

    result.toList should equal(List(Map("a" -> 42, "a.id" -> 42)))
  }

  // TCK'd
  test("should not get into a never ending loop") {
    val n = createNode("id" -> 42)

    val result = executeWithAllPlannersAndCompatibilityMode("MATCH (n) RETURN n, count(n) + 3")

    result.toList should equal(List(Map("n" -> n, "count(n) + 3" -> 4)))
  }

  // TCK'd
  test("renaming in multiple steps should still work") {
    val result = executeWithCostPlannerOnly(
      """CREATE (m)
        |WITH {FIRST: id(m)} AS m
        |WITH {SECOND: m.FIRST} AS m
        |RETURN m.SECOND""".stripMargin)
      .columnAs[Any]("m.SECOND")
      .toList

    result shouldNot contain(null)
  }

  // TCK'd
  test("aggregating by an array property has a correct definition of equality") {
    updateWithBothPlannersAndCompatibilityMode(
      """    create
        |    (_0  {a:[1,2,3]}),
        |    (_1  {a:[1,2,3]})
      """.stripMargin
    )

    val result = executeWithAllPlannersAndCompatibilityMode("MATCH (a) WITH a.a AS a, count(*) AS count RETURN count")

    result.toList should equal(List(Map("count" -> 2)))
  }

  // TCK'd
  test("reusing variable names should not be problematic") {
    val result = executeWithAllPlannersAndCompatibilityMode(
      """MATCH (person:Person       )<-                               -(message)<-[like]-(liker:Person)
        |WITH                 like.creationDate AS likeTime, person AS person
        |ORDER BY likeTime         , message.id
        |WITH        head(collect({              likeTime: likeTime})) AS latestLike, person AS person
        |RETURN latestLike.likeTime AS likeTime
        |ORDER BY likeTime
        | """.stripMargin)

    result shouldBe empty
  }

  // TCK'd
  test("compiled runtime should support addition of lists") {
    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("RETURN [1,2,3] + [4, 5] AS FOO")

    result.toComparableResult should equal(List(Map("FOO" -> List(1, 2, 3, 4, 5))))
  }

  // TCK'd
  test("compiled runtime should support addition of item to a list") {
    val result = executeWithAllPlannersAndRuntimesAndCompatibilityMode("""RETURN [1,2,3] + 4 AS FOO""")

    result.toComparableResult should equal(List(Map("FOO" -> List(1, 2, 3, 4))))
  }

  // TCK'd
  test("distinct inside aggregation should work with lists inside maps") {
    val list = Array("A", "B")
    createNode("array" -> list)
    createNode("array" -> list)

    val result = executeWithAllPlanners("MATCH (n) RETURN count(distinct {foo: n.array}) as count")

    result.toList should equal(List(Map("count" -> 1)))
  }

  // TCK'd
  test("distinct should work with lists inside maps") {
    val list = Array("A", "B")
    createNode("array" -> list)
    createNode("array" -> list)

    val result = executeWithAllPlanners("MATCH (n) WITH distinct {foo: n.array} as map RETURN count(*)")

    result.toList should equal(List(Map("count(*)" -> 1)))
  }

  // TCK'd
  test("distinct inside aggregation should work with nested lists inside map") {
    val list = Array("A", "B")
    createNode("array" -> list)
    createNode("array" -> list)

    val result = executeWithAllPlanners("MATCH (n) RETURN count(distinct {foo: [[n.array, n.array], [n.array, n.array]]}) as count")

    result.toList should equal(List(Map("count" -> 1)))
  }

  // TCK'd
  test("distinct inside aggregation should work with nested lists of maps inside map") {
    val list = Array("A", "B")
    createNode("array" -> list)
    createNode("array" -> list)

    val result = executeWithAllPlanners("MATCH (n) RETURN count(distinct {foo: [{bar: n.array}, {baz: {apa: n.array}}]}) as count")

    result.toList should equal(List(Map("count" -> 1)))
  }
}
