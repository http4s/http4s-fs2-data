/*
 * Copyright 2024 http4s.org
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

package org.http4s.fs2data.json

import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import fs2.data.json._
import fs2.data.json.literals.JsonInterpolator
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.EntityDecoder
import org.http4s.Request

class JsonEventSuite extends CatsEffectSuite with ScalaCheckEffectSuite {
  test("round-trip Json") {
    val in = json"""{"a": 1, "b": [true, false, null], "c": {"d": "e"}, "d": 1.2e3, "b": null}"""
    Stream
      .force(Request[IO]().withEntity(in.lift[IO]).as[Stream[IO, Token]])
      .compile
      .toList
      .map(_.asRight[Throwable])
      .assertEquals(in.toList)
  }

  test("round-trip Json string") {
    val in = """{"a":1,"b":[true,false,null],"c":{"d":"e"},"d":1.2e3,"b":null}"""
    Request[IO]()
      .withEntity(in)
      .as[Stream[IO, Token]]
      .map(Request[IO]().withEntity(_))
      .flatMap(EntityDecoder.text[IO].decode(_, false).value)
      .assertEquals(Right(in))
  }

  test("round-trip Json pretty printing") {
    val in = """{
    |  "a": 1,
    |  "b": [
    |    true,
    |    false,
    |    null
    |  ],
    |  "c": {
    |    "d": "e"
    |  },
    |  "d": 1.2e3,
    |  "b": null
    |}""".stripMargin
    Request[IO]()
      .withEntity(in)
      .as[Stream[IO, Token]]
      .map(Request[IO]().withEntity(_)(jsonTokensEncoder[IO](prettyPrint = true)))
      .flatMap(EntityDecoder.text[IO].decode(_, false).value)
      .assertEquals(Right(in))
  }
}
