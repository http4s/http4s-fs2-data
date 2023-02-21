/*
 * Copyright 2023 http4s.org
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

package org.http4s.fs2data.csv

import cats.data.NonEmptyList.{of => nel}
import cats.effect.IO
import fs2.Stream
import fs2.data.csv._
import fs2.data.csv.generic.semiauto
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.Charset
import org.http4s.EntityDecoder
import org.http4s.EntityEncoder
import org.http4s.Request

class CsvSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  private case class Data(first: String, second: Long, third: Boolean)

  private object Data {
    implicit val encoder: CsvRowEncoder[Data, String] = semiauto.deriveCsvRowEncoder
    implicit val decoder: CsvRowDecoder[Data, String] = semiauto.deriveCsvRowDecoder
  }

  test("round-trip rows") {
    implicit val encoder: EntityEncoder[IO, Stream[IO, Row]] = rowEncoder[IO]()
    implicit val decoder: EntityDecoder[IO, Stream[IO, Row]] = rowDecoder[IO]()
    val in = List(
      Row(nel("a", "2", "true"), Some(1)),
      Row(nel("b", "3", "false"), Some(2)),
    )
    Stream
      .force(Request[IO]().withEntity(Stream.emits(in).covary[IO]).as[Stream[IO, Row]])
      .compile
      .toList
      .assertEquals(in)
  }

  test("support charsets") {
    implicit val charset: Charset = Charset.`UTF-16`
    implicit val encoder: EntityEncoder[IO, Stream[IO, Row]] = rowEncoder[IO]()
    implicit val decoder: EntityDecoder[IO, Stream[IO, Row]] = rowDecoder[IO]()
    val in = List(
      Row(nel("äöüøé", "3", "false"), Some(1))
    )
    Stream
      .force(Request[IO]().withEntity(Stream.emits(in).covary[IO]).as[Stream[IO, Row]])
      .compile
      .toList
      .assertEquals(in) >>
      Request[IO]()
        .withEntity(Stream.emits(in).covary[IO])
        .as[String] // implicit charset is picked up here as well
        .assertEquals("äöüøé,3,false\n")
  }

  test("round-trip case classes with headers") {
    implicit val encoder: EntityEncoder[IO, Stream[IO, Data]] =
      csvEncoderForPipe(encodeUsingFirstHeaders())
    implicit val decoder: EntityDecoder[IO, Stream[IO, Data]] =
      csvDecoderForPipe(decodeUsingHeaders[Data]())
    val in = List(Data("a", 2, true), Data("b", 3, false))
    Stream
      .force(Request[IO]().withEntity(Stream.emits(in).covary[IO]).as[Stream[IO, Data]])
      .compile
      .toList
      .assertEquals(in)
  }

}
