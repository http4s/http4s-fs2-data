# http4s-fs2-data

`http4s-fs2-data` provides a set of integration libraries that integrate `http4s` with the streaming parsers offered by [fs2-data](https://github.com/gnieh/fs2-data).

## http4s-fs2-data-xml

Provides basic support for parsing and encoding `fs2.data.xml.XmlEvent` streams that can be handled in a streaming fashion
using the pipes and builders `fs2-data` provides.

```scala
libraryDependencies += "org.http4s" %% "http4s-fs2-data-xml" % "@VERSION@"
```

## http4s-fs2-data-xml-scala

Provides additional integration with `scala-xml` to work directly with its `Document` ans `Elem` types. To some extent, 
this module is a drop-in replacement for the `http4s-scala-xml` module, but it provides additional streaming capabilities
`scala-xml` doesn't.

```scala
libraryDependencies += "org.http4s" %% "http4s-fs2-data-xml-scala" % "@VERSION@"
```

### Example

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

## http4s-fs2-data-csv

Provides basic support for parsing and encoding CSV streams that can be handled in a streaming fashion
using the pipes `fs2-data` provides. Note that this integration does not expose any implicits, but due to the varity of 
CSV styles (comma/semicolon/tab for example) it exposes methods that lift the collection of CSV pipes `fs2-data` offers
to HTTP level so that all parsing options can conveniently be specified. Implicit encoder and decoder can easily be built
on top of this if the options are fixed with a certain context.

### Example

This example parses a CSV input with headers and outputs the parsed data as a JSON array.

```scala mdoc
import fs2.Stream
import fs2.data.csv._
import fs2.data.csv.generic.semiauto
import org.http4s.circe.CirceEntityEncoder._

class CsvStatsHttpEndpoint[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {
  private case class Person(name: String, age: Int)
  private object Person {
    implicit val decoder: CsvRowDecoder[Person, String] = semiauto.deriveCsvRowDecoder
  }
  
  private implicit val personDecoder: EntityDecoder[F, Stream[F, Person]] =
    org.http4s.fs2data.csv.csvDecoderForPipe(decodeUsingHeaders[Person]())

  // or more generic if you have a couple of similar inputs
  // private implicit def genericDecoder[T](implicit T: CsvRowDecoder[T, String]): EntityDecoder[F, Stream[F, T]] =
  //   org.http4s.fs2data.csv.csvDecoderForPipe(decodeUsingHeaders[T]())

  val service: HttpRoutes[F] = HttpRoutes.of { 
    case req @ POST -> Root / "csv" / "toJson" =>
      Ok(Stream.force(req.as[Stream[F, Person]]).compile.toList)
  }
}
```

## http4s-fs2-data-cbor

Provides basic support for parsing and encoding CBOR streams that can be handled in a streaming fashion, either
treating the in/output as a stream itself or as a single value.

### Example

This example consumes a CSV input, converts it to CBOR as a simple stream of arrays provides and returns it.

```scala mdoc
import fs2.Stream
import fs2.data.cbor.high.CborValue
import fs2.data.csv._
import org.http4s.fs2data.cbor._
import org.http4s.fs2data.csv._

class Csv2CborHttpEndpoint[F[_]](implicit F: Async[F]) extends Http4sDsl[F] {

  private implicit val decoder: EntityDecoder[F, Stream[F, Row]] = rowDecoder()
  
  val service: HttpRoutes[F] = HttpRoutes.of { 
    case req @ POST -> Root / "csv" / "toCbor" =>
      Ok(Stream.force(req.as[Stream[F, Row]]).map(toCbor))
  }
  
  private def toCbor(row: Row): CborValue =
    CborValue.Array(row.values.toList.map(CborValue.TextString(_)), false)
}
```

You can try yourself with this snippet:

```shell
curl -s -X "POST" "http://localhost:8080/csv/toCbor" \
     -H 'Content-Type: text/csv; charset=utf-8' \
     -d $'1,Ene,Mene
2,Muh,!' | od -A n -t x1
```

Then copy the output to [https://cbor.me](https://cbor.me) or a similar CBOR viewer. Make sure to view as `cborseq` otherwise the output will be truncated.

