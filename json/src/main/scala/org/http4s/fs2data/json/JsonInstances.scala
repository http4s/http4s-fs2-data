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

package org.http4s
package fs2data.json

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.applicative._
import cats.syntax.monadError._
import fs2.data.json._
import fs2.Stream
import cats.syntax.show._
import org.http4s.Charset.`UTF-8`
import org.http4s.headers.{`Content-Type`, `Transfer-Encoding`}

trait JsonInstances {

  implicit def jsonTokensDecoder[F[_]: Concurrent]: EntityDecoder[F, Stream[F, Token]] =
    EntityDecoder.decodeBy(MediaType.application.json) { msg =>
      DecodeResult.successT(
        msg.bodyText
          .through(tokens)
          .adaptError { case ex: JsonException =>
            MalformedMessageBodyFailure(
              s"Invalid Json (${ex.context.fold("No context")(jc => jc.show)}): ${ex.msg}",
              Some(ex),
            )
          }
      )
    }

  def jsonTokensEncoder[F[_]](prettyPrint: Boolean)(implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Stream[F, Token]] = EntityEncoder.encodeBy(
    Headers(
      `Content-Type`(MediaType.application.json).withCharset(charset),
      `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
    )
  ) { tokens =>
    Entity(
      tokens
        .through(
          if (prettyPrint) render.pretty() else render.compact
        )
        .through(fs2.text.encode[F](charset.nioCharset))
    )
  }

  implicit def jsonTokensEncoder[F[_]]: EntityEncoder[F, Stream[F, Token]] =
    jsonTokensEncoder(prettyPrint = false)

}
