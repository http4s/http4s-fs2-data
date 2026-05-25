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

package org.http4s
package fs2data.msgpack

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.RaiseThrowable
import fs2.Stream
import fs2.data.msgpack.MsgpackException
import fs2.data.msgpack.high.ast.MsgpackValue
import fs2.data.msgpack.high.ast.valuesFromBytes
import fs2.data.msgpack.high.ast.valuesToBytes
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Transfer-Encoding`

trait MsgpackInstances {

  private val msgpackMediaType: MediaType =
    new MediaType("application", "vnd.msgpack", binary = true)

  implicit def msgpackValueEncoder[F[_]: RaiseThrowable]: EntityEncoder[F, MsgpackValue] =
    msgpackValueStreamEncoder[F]
      .withContentType(`Content-Type`(msgpackMediaType))
      .contramap(Stream.emit)

  implicit def msgpackValueStreamEncoder[F[_]: RaiseThrowable]: EntityEncoder[F, Stream[F, MsgpackValue]] =
    EntityEncoder.encodeBy(
      Headers(
        `Content-Type`(msgpackMediaType),
        `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
      )
    )(values => Entity(values.through(valuesToBytes)))

  implicit def msgpackValueDecoder[F[_]](implicit
      F: Concurrent[F]
  ): EntityDecoder[F, MsgpackValue] =
    EntityDecoder.decodeBy(msgpackMediaType) { msg =>
      DecodeResult(
        msg.body
          .through(valuesFromBytes)
          .adaptError { case ex: MsgpackException =>
            MalformedMessageBodyFailure(s"Invalid MessagePack (${ex.getMessage})", Some(ex))
          }
          .compile
          .onlyOrError
          .map(_.asRight[DecodeFailure])
          .recover {
            case d: DecodeFailure => d.asLeft
            case t: NoSuchElementException =>
              MalformedMessageBodyFailure(t.getMessage, Some(t)).asLeft
            case t: IllegalStateException =>
              MalformedMessageBodyFailure(t.getMessage, Some(t)).asLeft
          }
      )
    }

  implicit def msgpackValueStreamDecoder[F[_]](implicit
      F: Concurrent[F]
  ): EntityDecoder[F, Stream[F, MsgpackValue]] =
    EntityDecoder.decodeBy(msgpackMediaType) { msg =>
      DecodeResult.successT(
        msg.body
          .through(valuesFromBytes)
          .adaptError { case ex: MsgpackException =>
            MalformedMessageBodyFailure(s"Invalid MessagePack (${ex.getMessage})", Some(ex))
          }
      )
    }

}
