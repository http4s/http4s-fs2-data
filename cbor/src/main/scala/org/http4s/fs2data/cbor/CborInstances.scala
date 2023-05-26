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

package org.http4s
package fs2data.cbor

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
import fs2.data.cbor._
import fs2.data.cbor.high.CborValue
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Transfer-Encoding`

trait CborInstances {

  implicit def cborValueEncoder[F[_]]: EntityEncoder[F, CborValue] =
    cborValueStreamEncoder[F]
      .withContentType(`Content-Type`(MediaType.application.cbor))
      .contramap(Stream.emit)

  implicit def cborValueStreamEncoder[F[_]]: EntityEncoder[F, Stream[F, CborValue]] =
    EntityEncoder.encodeBy(
      Headers(
        `Content-Type`(MediaType.application.`cbor-seq`),
        `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
      )
    )(values => Entity(values.through(high.toBinary)))

  implicit def cborValueDecoder[F[_]](implicit
      F: Concurrent[F]
  ): EntityDecoder[F, CborValue] =
    EntityDecoder.decodeBy(MediaType.application.cbor) { msg =>
      DecodeResult(
        msg.body
          .through(high.values)
          .adaptError { case ex: CborException =>
            MalformedMessageBodyFailure(s"Invalid CBOR (${ex.getMessage})", Some(ex))
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

  implicit def cborValueStreamDecoder[F[_]](implicit
      F: Concurrent[F]
  ): EntityDecoder[F, Stream[F, CborValue]] =
    EntityDecoder.decodeBy(MediaType.application.`cbor-seq`) { msg =>
      DecodeResult.successT(
        msg.body
          .through(high.values)
          .adaptError { case ex: CborException =>
            MalformedMessageBodyFailure(s"Invalid CBOR (${ex.getMessage})", Some(ex))
          }
      )
    }

}
