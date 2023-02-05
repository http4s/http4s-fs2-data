/*
 * Copyright 2022 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package fs2data.xml.scalaxml

import cats.effect._
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream
import fs2.text.decodeWithCharset
import fs2.text.utf8
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.Status.Ok
import org.http4s.fs2data.xml.scalaxml.generators._
import org.http4s.headers.`Content-Type`
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Prop._
import org.scalacheck.effect.PropF._
import org.typelevel.ci._

import java.nio.charset.StandardCharsets
import scala.xml.Document
import scala.xml.Elem

class ScalaXmlSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  def getBody(body: EntityBody[IO]): IO[String] =
    body.through(utf8.decode).foldMonoid.compile.lastOrError

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): IO[String] =
    Stream
      .emit(W.toEntity(a))
      .flatMap(_.body)
      .through(utf8.decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))

  val server: Request[IO] => IO[Response[IO]] = { req =>
    req.decode { (doc: Document) =>
      IO.pure(Response[IO](Ok).withEntity(doc.docElem.label))
    }
  }

  test("round trips utf-8") {
    forAllF(genXml) { (elem: Elem) =>
      Request[IO]()
        .withEntity(elem)
        .as[Document]
        .map(_.docElem)
        .assertEquals(elem)
    }
  }

  test("parse XML in parallel") {
    val req = Request().withEntity(
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><html><h1>h1</h1></html>"""
    )
    // https://github.com/http4s/http4s/issues/1209
    (0 to 5).toList
      .parTraverse(_ => server(req).flatMap(_.as[String]))
      .map { bodies =>
        bodies.foreach { body =>
          assertEquals(body, "html")
        }
      }
  }

  test("return 400 on parse error") {
    val body = "This is not XML."
    val tresp = server(Request[IO]().withEntity(body))
    tresp.map(_.status).assertEquals(Status.BadRequest)
  }

  test("htmlEncoder renders HTML") {
    val html = <html><body>Hello</body></html>
    implicit val cs: Charset = Charset.`UTF-8`
    assertIO(
      writeToString(html),
      """<?xml version="1.0" encoding="UTF-8"?><html><body>Hello</body></html>""",
    )
  }

  test("encode to UTF-8") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlElemEncoder[IO](Charset.`UTF-8`)
        .toEntity(hello)
        .body
        .through(fs2.text.utf8.decode)
        .compile
        .string,
      """<?xml version="1.0" encoding="UTF-8"?><hello name="Günther"/>""",
    )
  }

  test("encode to UTF-16") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlElemEncoder[IO](Charset.`UTF-16`)
        .toEntity(hello)
        .body
        .through(decodeWithCharset(StandardCharsets.UTF_16))
        .compile
        .string,
      """<?xml version="1.0" encoding="UTF-16"?><hello name="Günther"/>""",
    )
  }

  test("encode to ISO-8859-1") {
    val hello = <hello name="Günther"/>
    assertIO(
      xmlElemEncoder[IO](Charset.`ISO-8859-1`)
        .toEntity(hello)
        .body
        .through(decodeWithCharset(StandardCharsets.ISO_8859_1))
        .compile
        .string,
      """<?xml version="1.0" encoding="ISO-8859-1"?><hello name="Günther"/>""",
    )
  }

  property("encoder sets charset of Content-Type") {
    forAll { (cs: Charset) =>
      assertEquals(
        xmlElemEncoder[IO](cs).headers.get[`Content-Type`].flatMap(_.charset),
        Some(cs),
      )
    }
  }

  private def encodingTest(bytes: Chunk[Byte], contentType: String, name: String) = {
    val body = Stream.chunk(bytes)
    val msg = Request[IO](Method.POST, headers = Headers(Header.Raw(ci"Content-Type", contentType)))
      .withBodyStream(body)
    msg.as[Document].map(_ \\ "hello" \@ "name").assertEquals(name)
  }

  test("parse UTF-8 charset with explicit encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.1
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-8"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_8
        )
      ),
      "application/xml; charset=utf-8",
      "Günther",
    )
  }

  test("parse UTF-8 charset with no encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.1
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_8)
      ),
      "application/xml; charset=utf-8",
      "Günther",
    )
  }

  test("parse UTF-16 charset with explicit encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.2
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16
        )
      ),
      "application/xml; charset=utf-16",
      "Günther",
    )
  }

  test("parse UTF-16 charset with no encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.2
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_16)
      ),
      "application/xml; charset=utf-16",
      "Günther",
    )
  }

  test("parse omitted charset and 8-Bit MIME Entity") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.3
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="iso-8859-1"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.ISO_8859_1
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset and 16-Bit MIME Entity") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.4
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset and 16-Bit MIME Entity (big endian)") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.4 (second example)
    encodingTest(
      Chunk(0xfe.toByte, 0xff.toByte) ++ Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16BE
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset and 16-Bit MIME Entity (little endian)") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.4 (second example)
    encodingTest(
      Chunk(0xff.toByte, 0xfe.toByte) ++ Chunk.array(
        """<?xml version="1.0" encoding="utf-16"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.UTF_16LE
        )
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse omitted charset, no internal encoding declaration") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.5
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_8)
      ),
      "application/xml",
      "Günther",
    )
  }

  test("parse utf-16be charset") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.6
    encodingTest(
      Chunk.array(
        """<?xml version="1.0"?><hello name="Günther"/>""".getBytes(StandardCharsets.UTF_16BE)
      ),
      "application/xml; charset=utf-16be",
      "Günther",
    )
  }

  test("parse non-utf charset") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.7
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="iso-2022-kr"?><hello name="문재인"/>""".getBytes(
          "iso-2022-kr"
        )
      ),
      "application/xml; charset=iso-2022-kr",
      "문재인",
    ).unlessA(
      sys.props("java.vm.name") === "Scala.js" || sys.props("java.vm.name") === "Scala Native"
    ) // exclude test on Scala.js / Scala Native as they don't support this charset
  }

  test("parse conflicting charset and internal encoding") {
    // https://datatracker.ietf.org/doc/html/rfc7303#section-8.8
    encodingTest(
      Chunk.array(
        """<?xml version="1.0" encoding="utf-8"?><hello name="Günther"/>""".getBytes(
          StandardCharsets.ISO_8859_1
        )
      ),
      "application/xml; charset=iso-8859-1",
      "Günther",
    )
  }
}
