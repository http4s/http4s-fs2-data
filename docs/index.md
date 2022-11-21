# http4s-fs2-data-xml

Provides basic support for parsing and encoding `fs2.data.xml.XmlEvent` streams that can be handled in a streaming fashion
using the pipes and builders `fs2-data` provides.

```scala
libraryDependencies += "org.http4s" %% "http4s-fs2-data-xml" % "@VERSION@"
```

# http4s-fs2-data-xml-scala

Provides additional integration with `scala-xml` to work directly with its `Document` ans `Elem` types. To some extent, 
this module is a drop-in replacement for the `http4s-scala-xml` module, but it provides additional streaming capabilities
`scala-xml` doesn't.

```scala
libraryDependencies += "org.http4s" %% "http4s-fs2-data-xml-scala" % "@VERSION@"
```

## Example

```scala mdoc
import cats.effect.Async
import cats.syntax.flatMap._
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{ApiVersion => _, _}

import scala.xml._

val ApiVersion = "v1"

// Docs: http://http4s.org/latest/entity/
class JsonXmlHttpEndpoint[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {
  private case class Person(name: String, age: Int)

  /** XML Example for Person:
    *
    * <person>
    *   <name>gvolpe</name>
    *   <age>30</age>
    * </person>
    */
  private object Person {
    def fromXml(elem: Document): Person = {
      val name = (elem \\ "name").text
      val age = (elem \\ "age").text
      Person(name, age.toInt)
    }
  }

  private def personXmlDecoder: EntityDecoder[F, Person] =
    org.http4s.fs2data.xml.scalaxml.xmlDocumentDecoder.map(Person.fromXml)

  implicit private def jsonXmlDecoder: EntityDecoder[F, Person] =
    jsonOf[F, Person].orElse(personXmlDecoder)

  val service: HttpRoutes[F] = HttpRoutes.of {
    case GET -> Root / ApiVersion / "media" =>
      Ok(
        "Send either json or xml via POST method. Eg: \n{\n  \"name\": \"gvolpe\",\n  \"age\": 30\n}\n or \n <person>\n  <name>gvolpe</name>\n  <age>30</age>\n</person>"
      )

    case req @ POST -> Root / ApiVersion / "media" =>
      req.as[Person].flatMap { person =>
        Ok(s"Successfully decoded person: ${person.name}")
      }
  }
}
```
