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
