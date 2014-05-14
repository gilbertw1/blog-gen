---
title : Play 2: Serializing Case Classes with Anorm Pk Type to Json
tags : [scala, play2, anorm]
---

One small annoyance I ran into using the Play 2 framework with Anorm as the persistence provider was the inability to 
buy into the "Json Inception" functionality of the Play 2 json libraries by default. 

For the uniniated the above mentioned functionality is provided by macros that generate Reads/Writes/Formats for case
classes at compile time. This allows us to read and write json using only case classes (almost) without requiring any 
additional code.

For example, take this class:

```scala
  case class User (
    firstName: String,
    lastName: String,
    age: Int
  )
```

We can have a format generated for this class at runtime using Json inception:

```scala
    implicit val userFormat = format[User]
```

With the above format in implicit scope, converting a user to and from json is as easy as pie:

```scala
  import play.api.libs.json.Json._

  val user = User("Bryan", "Gilbert", 27) 
  // => User(Bryan,Gilbert,27)
  val userJson = stringify(toJson(user)) 
  // => {"firstName":"Bryan","lastName":"Gilbert","age":27}
  val newUser = parse(userJson).as[User] 
  // => User = User(Bryan,Gilbert,27)
```

All of the above works entirely as expected. Nice and Simple. However, there is one small hiccup when trying to use a case
class containing the Pk datatype from Anorm. The Pk datatype is a field that denotes a primary key in a relational database. 
Pk is an algebraic datatype of Pk\[T\] that can be either NotAssigned or it can be an Id(t) where t is of type T.

So now we can have a User class that corresponds to a database table User:

```scala
  case class User (
    id: Pk[Long] =  NotAssigned,
    firstName: String,
    lastName: String,
    age: Int
  )
```

This allows us to instantiate a user directly from the database using anorm, and properly represent the primary key both in cases 
where the User has or has not yet been inserted into the database. 

This is all great stuff, however we can no longer convert the User class as easily to and from Json as we did before. This is
because there is no format in implict scope that knows how to read and write the Pk type.

```scala
  implicit val userFormat = format[User] 
  // => error: No implicit format for anorm.Pk[Long] available.
```

The solution to this problem is to create a format for Pk that will properly allow serialization and deserialization:

```scala
  implicit object PkFormat extends Format[Pk[Long]] {
    def reads(json: JsValue): JsResult[Pk[Long]] = JsSuccess (
        json.asOpt[Long].map(id => Id(id)).getOrElse(NotAssigned)
    )
    def writes(id: Pk[Long]): JsValue = id.map(JsNumber(_)).getOrElse(JsNull)
  }

  implicit val userFormat = format[User]

  val user = User(Id(1), "Bryan", "Gilbert", 27) 
  // => User(1,Bryan,Gilbert,27)
  val userJson = stringify(toJson(user)) 
  // => {"id":1,"firstName":"Bryan","lastName":"Gilbert","age":27}
  val newUser = parse(userJson).as[User] 
  // => User(1,Bryan,Gilbert,27)

  val user = User(NotAssigned, "Bryan", "Gilbert", 27) 
  // => User(NotAssigned,Bryan,Gilbert,27)
  val userJson = stringify(toJson(user)) 
  // => {"id":null,"firstName":"Bryan","lastName":"Gilbert","age":27}
  val newUser = parse(userJson).as[User] 
  // => User(NotAssigned,Bryan,Gilbert,27)
```

Note that I wasn't completely satisfied with outputing null in the Json in the case of NotAssigned, however it seemed
like the closest mapping I could find.

Hope this helps someone!