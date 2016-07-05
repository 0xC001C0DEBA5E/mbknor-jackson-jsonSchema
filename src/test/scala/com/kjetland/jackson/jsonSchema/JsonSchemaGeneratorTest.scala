package com.kjetland.jackson.jsonSchema

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonValue}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.kjetland.jackson.jsonSchema.testData._
import org.scalatest.{FunSuite, Matchers}

import scala.collection.JavaConversions._

class JsonSchemaGeneratorTest extends FunSuite with Matchers {

  val _objectMapper = new ObjectMapper()
  val _objectMapperScala = new ObjectMapper()
  _objectMapperScala.registerModule(new DefaultScalaModule)

  List(_objectMapper, _objectMapperScala).foreach {
    om =>
      val simpleModule = new SimpleModule()
      simpleModule.addSerializer(classOf[PojoWithCustomSerializer], new PojoWithCustomSerializerSerializer)
      simpleModule.addDeserializer(classOf[PojoWithCustomSerializer], new PojoWithCustomSerializerDeserializer)
      om.registerModule(simpleModule)
  }



  val jsonSchemaGenerator = new JsonSchemaGenerator(_objectMapper, debug = true)
  val jsonSchemaGeneratorScala = new JsonSchemaGenerator(_objectMapperScala, debug = true)

  val testData = new TestData{}

  def asPrettyJson(node:JsonNode, om:ObjectMapper):String = {
    om.writerWithDefaultPrettyPrinter().writeValueAsString(node)
  }


  // Asserts that we're able to go from object => json => equal object
  def assertToFromJson(g:JsonSchemaGenerator, o:Any): JsonNode = {
    assertToFromJson(g, o, o.getClass)
  }

  // Asserts that we're able to go from object => json => equal object
  // deserType might be a class which o extends (polymorphism)
  def assertToFromJson(g:JsonSchemaGenerator, o:Any, deserType:Class[_]): JsonNode = {
    val json = g.rootObjectMapper.writeValueAsString(o)
    println(s"json: $json")
    val jsonNode = g.rootObjectMapper.readTree(json)
    val r = g.rootObjectMapper.treeToValue(jsonNode, deserType)
    assert( o == r)
    jsonNode
  }

  def useSchema(jsonSchema:JsonNode, jsonToTestAgainstSchema:Option[JsonNode] = None): Unit = {
    val schemaValidator = JsonSchemaFactory.byDefault().getJsonSchema(jsonSchema)
    jsonToTestAgainstSchema.foreach {
      node =>
        val r = schemaValidator.validate(node)
        if ( !r.isSuccess ) {
          throw new Exception("json does not validate agains schema: " + r)
        }

    }
  }

  // Generates schema, validates the schema using external schema validator and
  // Optionally tries to validate json against the schema.
  def generateAndValidateSchema(g:JsonSchemaGenerator, clazz:Class[_], jsonToTestAgainstSchema:Option[JsonNode] = None):JsonNode = {
    val schema = g.generateJsonSchema(clazz)

    println("--------------------------------------------")
    println(asPrettyJson(schema, g.rootObjectMapper))

    assert( JsonSchemaGenerator.JSON_SCHEMA_DRAFT_4_URL == schema.at("/$schema").asText())

    useSchema(schema, jsonToTestAgainstSchema)

    schema
  }

  def assertJsonSubTypesInfo(node:JsonNode, typeParamName:String, typeName:String): Unit ={
    /*
      "properties" : {
        "type" : {
          "type" : "string",
          "enum" : [ "child1" ],
          "default" : "child1"
        },
      },
      "title" : "child1",
      "required" : [ "type" ]
    */
    assert( node.at(s"/properties/$typeParamName/type").asText() == "string" )
    assert( node.at(s"/properties/$typeParamName/enum/0").asText() == typeName)
    assert( node.at(s"/properties/$typeParamName/default").asText() == typeName)
    assert( node.at(s"/title").asText() == typeName)
    assert( getRequiredList(node).contains(typeParamName))

  }

  def getArrayNodeAsListOfStrings(node:JsonNode):List[String] = {
    node.asInstanceOf[ArrayNode].iterator().toList.map(_.asText())
  }

  def getRequiredList(node:JsonNode):List[String] = {
    getArrayNodeAsListOfStrings(node.at(s"/required"))
  }

  def getNodeViaArrayOfRefs(root:JsonNode, pathToArrayOfRefs:String, definitionName:String):JsonNode = {
    val nodeWhereArrayOfRefsIs:ArrayNode = root.at(pathToArrayOfRefs).asInstanceOf[ArrayNode]
    val arrayItemNodes = nodeWhereArrayOfRefsIs.iterator().toList
    val ref = arrayItemNodes.map(_.get("$ref").asText()).find( _.endsWith(s"/$definitionName")).get
    // use ref to look the node up
    val fixedRef = ref.substring(1) // Removing starting #
    root.at(fixedRef)
  }

  def getNodeViaRefs(root:JsonNode, nodeWithRef:JsonNode, definitionName:String):ObjectNode = {
    val ref = nodeWithRef.at("/$ref").asText()
    assert( ref.endsWith(s"/$definitionName"))
    // use ref to look the node up
    val fixedRef = ref.substring(1) // Removing starting #
    root.at(fixedRef).asInstanceOf[ObjectNode]
  }

  test("Generate scheme for plain class not using @JsonTypeInfo") {

    def doTest(pojo:Object, clazz:Class[_], g:JsonSchemaGenerator): Unit = {

      val jsonNode = assertToFromJson(g, pojo)
      val schema = generateAndValidateSchema(g, clazz, Some(jsonNode))

      assert( false == schema.at("/additionalProperties").asBoolean())
      assert( schema.at("/properties/someString/type").asText() == "string")
    }

    doTest(testData.classNotExtendingAnything, testData.classNotExtendingAnything.getClass, jsonSchemaGenerator)
    doTest(testData.classNotExtendingAnythingScala, testData.classNotExtendingAnythingScala.getClass, jsonSchemaGeneratorScala)

  }

  test("Generating schema for concrete class which happens to extend class using @JsonTypeInfo") {

    def doTest(pojo:Object, clazz:Class[_], g:JsonSchemaGenerator): Unit = {
      val jsonNode = assertToFromJson(g, pojo)
      val schema = generateAndValidateSchema(g, clazz, Some(jsonNode))

      assert( false == schema.at("/additionalProperties").asBoolean())
      assert( schema.at("/properties/parentString/type").asText() == "string")
      assertJsonSubTypesInfo(schema, "type", "child1")
    }

    doTest(testData.child1, testData.child1.getClass, jsonSchemaGenerator)
    doTest(testData.child1Scala, testData.child1Scala.getClass, jsonSchemaGeneratorScala)
  }

  test("Generate schema for regular class which has a property of class annotated with @JsonTypeInfo") {

    // Java
    {
      val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.pojoWithParent)
      val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.pojoWithParent.getClass, Some(jsonNode))

      assert(false == schema.at("/additionalProperties").asBoolean())
      assert(schema.at("/properties/pojoValue/type").asText() == "boolean")

      assertChild1(schema, "/properties/child/oneOf")
      assertChild2(schema, "/properties/child/oneOf")

    }

    // Scala
    {
      val jsonNode = assertToFromJson(jsonSchemaGeneratorScala, testData.pojoWithParentScala)
      val schema = generateAndValidateSchema(jsonSchemaGeneratorScala, testData.pojoWithParentScala.getClass, Some(jsonNode))

      assert(false == schema.at("/additionalProperties").asBoolean())
      assert(schema.at("/properties/pojoValue/type").asText() == "boolean")

      assertChild1(schema, "/properties/child/oneOf", "Child1Scala")
      assertChild2(schema, "/properties/child/oneOf", "Child2Scala")

    }

  }

  def assertChild1(node:JsonNode, path:String, defName:String = "Child1"): Unit ={
    val child1 = getNodeViaArrayOfRefs(node, path, defName)
    assertJsonSubTypesInfo(child1, "type", "child1")
    assert( child1.at("/properties/parentString/type").asText() == "string" )
    assert( child1.at("/properties/child1String/type").asText() == "string" )
  }

  def assertChild2(node:JsonNode, path:String, defName:String = "Child2"): Unit ={
    val child2 = getNodeViaArrayOfRefs(node, path, defName)
    assertJsonSubTypesInfo(child2, "type", "child2")
    assert( child2.at("/properties/parentString/type").asText() == "string" )
    assert( child2.at("/properties/child2int/type").asText() == "integer" )
  }

  test("Generate schema for super class annotated with @JsonTypeInfo") {

    // Java
    {
      val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.child1)
      assertToFromJson(jsonSchemaGenerator, testData.child1, classOf[Parent])

      val schema = generateAndValidateSchema(jsonSchemaGenerator, classOf[Parent], Some(jsonNode))

      assertChild1(schema, "/oneOf")
      assertChild2(schema, "/oneOf")
    }

    // Scala
    {
      val jsonNode = assertToFromJson(jsonSchemaGeneratorScala, testData.child1Scala)
      assertToFromJson(jsonSchemaGeneratorScala, testData.child1Scala, classOf[ParentScala])

      val schema = generateAndValidateSchema(jsonSchemaGeneratorScala, classOf[ParentScala], Some(jsonNode))

      assertChild1(schema, "/oneOf", "Child1Scala")
      assertChild2(schema, "/oneOf", "Child2Scala")
    }

  }

  test("primitives") {
    val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.manyPrimitives)
    val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.manyPrimitives.getClass, Some(jsonNode))

    assert( schema.at("/properties/_string/type").asText() == "string" )

    assert( schema.at("/properties/_integer/type").asText() == "integer" )
    assert( !getRequiredList(schema).contains("_integer")) // Should allow null by default

    assert( schema.at("/properties/_int/type").asText() == "integer" )
    assert( getRequiredList(schema).contains("_int")) // Must have a value

    assert( schema.at("/properties/_booleanObject/type").asText() == "boolean" )
    assert( !getRequiredList(schema).contains("_booleanObject")) // Should allow null by default

    assert( schema.at("/properties/_booleanPrimitive/type").asText() == "boolean" )
    assert( getRequiredList(schema).contains("_booleanPrimitive")) // Must be required since it must have true or false - not null

    assert( schema.at("/properties/_booleanObjectWithNotNull/type").asText() == "boolean" )
    assert( getRequiredList(schema).contains("_booleanObjectWithNotNull"))

    assert( schema.at("/properties/_doubleObject/type").asText() == "number" )
    assert( !getRequiredList(schema).contains("_doubleObject")) // Should allow null by default

    assert( schema.at("/properties/_doublePrimitive/type").asText() == "number" )
    assert( getRequiredList(schema).contains("_doublePrimitive")) // Must be required since it must have a value - not null

    assert( schema.at("/properties/myEnum/type").asText() == "string")
    assert( getArrayNodeAsListOfStrings(schema.at("/properties/myEnum/enum")) == List("A", "B", "C") )


  }

  test("custom serializer not overriding JsonSerializer.acceptJsonFormatVisitor") {

    val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.pojoWithCustomSerializer)
    val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.pojoWithCustomSerializer.getClass, Some(jsonNode))
    assert( schema.asInstanceOf[ObjectNode].fieldNames().toList == List("$schema")) // Empyt schema due to custom serializer
  }

  test("object with property using custom serializer not overriding JsonSerializer.acceptJsonFormatVisitor") {

    val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.objectWithPropertyWithCustomSerializer)
    val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.objectWithPropertyWithCustomSerializer.getClass, Some(jsonNode))
    assert( schema.at("/properties/s/type").asText() == "string")
    assert( schema.at("/properties/child").asInstanceOf[ObjectNode].fieldNames().toList.size == 0)
  }


  test("pojoWithArrays") {

    def doTest(pojo:Object, clazz:Class[_], g:JsonSchemaGenerator): Unit ={

      val jsonNode = assertToFromJson(g, pojo)
      val schema = generateAndValidateSchema(g, clazz, Some(jsonNode))

      assert(schema.at("/properties/intArray1/type").asText() == "array")
      assert(schema.at("/properties/intArray1/items/type").asText() == "integer")

      assert(schema.at("/properties/stringArray/type").asText() == "array")
      assert(schema.at("/properties/stringArray/items/type").asText() == "string")

      assert(schema.at("/properties/stringList/type").asText() == "array")
      assert(schema.at("/properties/stringList/items/type").asText() == "string")

      assert(schema.at("/properties/polymorphismList/type").asText() == "array")
      assertChild1(schema, "/properties/polymorphismList/items/oneOf")
      assertChild2(schema, "/properties/polymorphismList/items/oneOf")

      assert(schema.at("/properties/polymorphismArray/type").asText() == "array")
      assertChild1(schema, "/properties/polymorphismArray/items/oneOf")
      assertChild2(schema, "/properties/polymorphismArray/items/oneOf")
    }

    doTest( testData.pojoWithArrays, testData.pojoWithArrays.getClass, jsonSchemaGenerator)
    doTest( testData.pojoWithArraysScala, testData.pojoWithArraysScala.getClass, jsonSchemaGeneratorScala)

  }

  test("recursivePojo") {
    val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.recursivePojo)
    val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.recursivePojo.getClass, Some(jsonNode))

    assert( schema.at("/properties/myText/type").asText() == "string")

    assert( schema.at("/properties/children/type").asText() == "array")
    val defViaRef = getNodeViaRefs(schema, schema.at("/properties/children/items"), "RecursivePojo")

    assert( defViaRef.at("/properties/myText/type").asText() == "string")
    assert( defViaRef.at("/properties/children/type").asText() == "array")
    val defViaRef2 = getNodeViaRefs(schema, defViaRef.at("/properties/children/items"), "RecursivePojo")

    assert( defViaRef == defViaRef2)

  }

  test("pojo using Maps") {
    val jsonNode = assertToFromJson(jsonSchemaGenerator, testData.pojoUsingMaps)
    val schema = generateAndValidateSchema(jsonSchemaGenerator, testData.pojoUsingMaps.getClass, Some(jsonNode))

    assert( schema.at("/properties/string2Integer/type").asText() == "object")
    assert( schema.at("/properties/string2Integer/additionalProperties").asBoolean() == true)

    assert( schema.at("/properties/string2String/type").asText() == "object")
    assert( schema.at("/properties/string2String/additionalProperties").asBoolean() == true)

    assert( schema.at("/properties/string2PojoUsingJsonTypeInfo/type").asText() == "object")
    assert( schema.at("/properties/string2PojoUsingJsonTypeInfo/additionalProperties").asBoolean() == true)
  }


}

trait TestData {
  import scala.collection.JavaConversions._
  val child1 = {
    val c = new Child1()
    c.parentString = "pv"
    c.child1String = "cs"
    c
  }

  val child1Scala = Child1Scala("pv", "cs")

  val child2 = {
    val c = new Child2()
    c.parentString = "pv"
    c.child2int = 12
    c
  }

  val child2Scala = Child2Scala("pv", 12)

  val pojoWithParent = {
    val p = new PojoWithParent
    p.pojoValue = true
    p.child = child1
    p
  }

  val pojoWithParentScala = PojoWithParentScala(true, child1Scala)

  val classNotExtendingAnything = {
    val o = new ClassNotExtendingAnything
    o.someString = "Something"
    o
  }

  val classNotExtendingAnythingScala = ClassNotExtendingAnythingScala("Something")

  val manyPrimitives = new ManyPrimitives("s1", 1, 2, true, false, true, 0.1, 0.2, MyEnum.B)

  val pojoWithCustomSerializer = {
    val p = new PojoWithCustomSerializer
    p.myString = "xxx"
    p
  }

  val objectWithPropertyWithCustomSerializer = new ObjectWithPropertyWithCustomSerializer("s1", pojoWithCustomSerializer)

  val pojoWithArrays = new PojoWithArrays(
    Array(1,2,3),
    Array("a1","a2","a3"),
    List("l1", "l2", "l3"),
    List(child1, child2),
    List(child1, child2).toArray,
    List(classNotExtendingAnything, classNotExtendingAnything)
  )

  val pojoWithArraysScala = PojoWithArraysScala(
    Some(List(1,2,3)),
    List("a1","a2","a3"),
    List("l1", "l2", "l3"),
    List(child1, child2),
    List(child1, child2),
    List(classNotExtendingAnything, classNotExtendingAnything)
  )

  val recursivePojo = new RecursivePojo("t1", List(new RecursivePojo("c1", null)))

  val pojoUsingMaps = new PojoUsingMaps(
      Map[String, Integer]("a" -> 1, "b" -> 2),
      Map("x" -> "y", "z" -> "w"),
      Map[String, Parent]("1" -> child1, "2" -> child2)
    )

}


case class PojoWithArraysScala
(
  intArray1:Option[List[Integer]], // We never use array in scala - use list instead to make it compatible with PojoWithArrays (java)
  stringArray:List[String], // We never use array in scala - use list instead to make it compatible with PojoWithArrays (java)
  stringList:List[String],
  polymorphismList:List[Parent],
  polymorphismArray:List[Parent], // We never use array in scala - use list instead to make it compatible with PojoWithArrays (java)
  regularObjectList:List[ClassNotExtendingAnything]
)

case class ClassNotExtendingAnythingScala(someString:String)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(Array(new JsonSubTypes.Type(value = classOf[Child1Scala], name = "child1"), new JsonSubTypes.Type(value = classOf[Child2Scala], name = "child2")))
trait ParentScala

case class Child1Scala(parentString:String, child1String:String) extends ParentScala
case class Child2Scala(parentString:String, child2int:Int) extends ParentScala

case class PojoWithParentScala(pojoValue:Boolean, child:ParentScala)