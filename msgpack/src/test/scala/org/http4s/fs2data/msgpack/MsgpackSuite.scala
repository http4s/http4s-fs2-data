/*
 * Copyright 2026 http4s.org
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

package org.http4s.fs2data.msgpack

import cats.effect.IO
import fs2.Stream
import fs2.data.msgpack.high.ast.MsgpackValue
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.http4s.Request
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF.forAllF
import scodec.bits.ByteVector

class MsgpackSuite extends CatsEffectSuite with ScalaCheckEffectSuite {

  private lazy val msgpackGen: Gen[MsgpackValue] = Gen.recursive[MsgpackValue] { msgpackGen =>
    Gen.oneOf(
      Arbitrary.arbitrary[Long].map(MsgpackValue.Integer(_)),
      Gen.asciiPrintableStr.map(MsgpackValue.String(_)),
      Gen
        .listOf(Gen.choose[Byte](Byte.MinValue, Byte.MaxValue))
        .map(l => MsgpackValue.Bin(ByteVector(l))),
      Arbitrary.arbitrary[Boolean].map(MsgpackValue.Boolean(_)),
      Arbitrary.arbitrary[Float].filter(f => !f.isNaN).map(MsgpackValue.Float(_)),
      Arbitrary.arbitrary[Double].filter(d => !d.isNaN).map(MsgpackValue.Double(_)),
      Gen.sized { size =>
        Gen.resize(
          size / 2,
          Gen.listOf(msgpackGen).map(MsgpackValue.Array(_)),
        )
      },
      Gen.const(MsgpackValue.Nil),
    )
  }

  private lazy val msgpackStreamGen: Gen[Stream[fs2.Pure, MsgpackValue]] =
    Gen.chooseNum(0, 5).flatMap(Gen.listOfN(_, msgpackGen)).map(Stream.emits)

  test("round-trip single MessagePack values") {
    forAllF(msgpackGen) { msgpack =>
      Stream
        .force {
          val req = Request[IO]().withEntity(Stream.emit(msgpack).covary[IO])
          req.as[Stream[IO, MsgpackValue]]
        }
        .compile
        .onlyOrError
        .assertEquals(msgpack, msgpack.toString)
    }
  }

  test("round-trip MessagePack value streams") {
    forAllF(msgpackStreamGen) { msgpacks =>
      Stream
        .force {
          val req = Request[IO]().withEntity(msgpacks.covary[IO])
          req.as[Stream[IO, MsgpackValue]]
        }
        .compile
        .toList
        .assertEquals(msgpacks.compile.toList, msgpacks.compile.toList.toString)
    }
  }

}
