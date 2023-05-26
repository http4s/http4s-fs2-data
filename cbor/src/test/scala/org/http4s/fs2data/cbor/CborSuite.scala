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

package org.http4s.fs2data.cbor

import cats.effect.IO
import fs2.{Fallible, Stream}
import fs2.data.cbor.high
import fs2.data.cbor.high._
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.Request
import org.scalacheck.effect.PropF.forAllF
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector

class CborSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  private lazy val cborGen: Gen[CborValue] = Gen.recursive[CborValue] { cborGen =>
    Gen.oneOf(
      Arbitrary
        .arbitrary[BigInt]
        .map(CborValue.Integer(_)),
      Gen
        .listOf(Gen.choose[Byte](Byte.MinValue, Byte.MaxValue))
        .map(l => CborValue.ByteString(ByteVector(l))),
      Gen.asciiPrintableStr.map(CborValue.TextString(_)),
      Arbitrary.arbitrary[Boolean].flatMap { indefinite =>
        Gen.sized { size =>
          Gen.resize(
            size / 2,
            Gen
              .listOf(cborGen)
              .flatMap(vals => CborValue.Array(vals, indefinite)),
          )
        }
      },
      Gen.oneOf(CborValue.True, CborValue.False, CborValue.Null, CborValue.Undefined),
    )
  }

  private lazy val cborStreamGen: Gen[Stream[fs2.Pure, CborValue]] =
    Gen.chooseNum(0, 5).flatMap(Gen.listOfN(_, cborGen)).map(Stream.emits)

  test("round-trip single CBOR values") {
    forAllF(cborGen) { cbor =>
      Stream
        .force {
          val req = Request[IO]().withEntity(Stream.emit(cbor).covary[IO])
          req.as[Stream[IO, CborValue]]
        }
        .compile
        .onlyOrError
        .assertEquals(cbor, renderDiagnostic(cbor))
    }
  }

  test("round-trip CBOR value streams") {
    forAllF(cborStreamGen) { cbors =>
      Stream
        .force {
          val req = Request[IO]().withEntity(cbors.covary[IO])
          req.as[Stream[IO, CborValue]]
        }
        .compile
        .toList
        .assertEquals(cbors.compile.toList, cbors.foldMap(renderDiagnostic).compile.string)
    }
  }

  // make for a good debug output
  private def renderDiagnostic(cbor: CborValue) =
    Stream(cbor)
      .covary[Fallible]
      .through(high.toItems)
      .through(fs2.data.cbor.diagnostic)
      .compile
      .string
      .map(_ + s"\n\n$cbor")
      .getOrElse(s"FAILED TO RENDER DIAGNOSTIC: $cbor")

  def convertBytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes)
      sb.append(String.format("%02x", Byte.box(b)))
    sb.toString
  }

}
