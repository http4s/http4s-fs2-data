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

package org.http4s.fs2data.xml

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import fs2.data.xml._
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.Charset
import org.http4s.EntityDecoder
import org.http4s.Request

class XmlEventSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  test("round-trip XML") {
    val in = xml"""<?xml version="1.0"?><root a="b"><something/></root>"""
    Stream
      .force(Request[IO]().withEntity(in.lift[IO]).as[Stream[IO, XmlEvent]])
      .compile
      .toList
      .map(_.asRight[Throwable])
      .assertEquals(in.toList)
  }

  test("round-trip XML string") {
    val in = """<?xml version="1.0"?><root a="b"><something/></root>"""
    Request[IO]()
      .withEntity(in)
      .as[Stream[IO, XmlEvent]]
      .map(Request[IO]().withEntity(_))
      .flatMap(EntityDecoder.text[IO].decode(_, false).value)
      .assertEquals(Right(in))
  }

  test("preserves comments by default") {
    val in = rawxml"""<?xml version="1.0"?><root><!-- here's a comment --></root>"""
    Stream
      .force(Request[IO]().withEntity(in.lift[IO]).as[Stream[IO, XmlEvent]])
      .compile
      .toList
      .map(_.asRight[Throwable])
      .assertEquals(in.toList)
  }

  test("can disable comments") {
    val in = rawxml"""<?xml version="1.0"?><root><!-- here's a comment --></root>"""
    Stream
      .force(
        Request[IO]()
          .withEntity(in.lift[IO])
          .as[Stream[IO, XmlEvent]](implicitly, xmlEventsDecoder(false))
      )
      .compile
      .toList
      .map(_.asRight[Throwable])
      .assertEquals(in.filterNot(_.isInstanceOf[XmlEvent.Comment]).toList)
  }

  test("supports charsets") {
    implicit val charset: Charset = Charset.`UTF-16`
    val in = xml"""<?xml version="1.0" encoding="utf-16"?><root a="b"><something/></root>"""
    Stream
      .force(Request[IO]().withEntity(in.lift[IO]).as[Stream[IO, XmlEvent]])
      .compile
      .toList
      .map(_.asRight[Throwable])
      .assertEquals(in.toList)
  }

  test("guesses charsets correctly") {
    def check(xml: String, cs: Charset) = assertEquals(guessCharset(xml), Right(cs))

    check("""<?xml version="1.0" encoding="utf-8"?>""", Charset.`UTF-8`)
    check("""<?xml version="1.0" encoding="utf-16" ?>""", Charset.`UTF-16`)
    check("""<?xml version="1.0" encoding="ascii"?>""", Charset.`US-ASCII`)
    check("""<?xml version='1.0' encoding='ascii'?>""", Charset.`US-ASCII`)
    // fallback
    check("""<?xml version="1.0"?>""", Charset.`UTF-8`)
    check("""garbage""", Charset.`UTF-8`)
    check("", Charset.`UTF-8`)
  }

}
