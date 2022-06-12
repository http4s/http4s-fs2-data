# http4s-scala-xml

```scala
libraryDependencies += "org.http4s" %% "http4s-scala-xml" % "@VERSION@"
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
    def fromXml(elem: Elem): Person = {
      val name = (elem \\ "name").text
      val age = (elem \\ "age").text
      Person(name, age.toInt)
    }
  }

  private def personXmlDecoder: EntityDecoder[F, Person] =
    org.http4s.scalaxml.xmlDecoder[F].map(Person.fromXml)

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
