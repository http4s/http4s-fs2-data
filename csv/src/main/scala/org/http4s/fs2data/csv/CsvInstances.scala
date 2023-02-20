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
package fs2data.csv

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Pipe
import fs2.Stream
import fs2.data.csv._
import org.http4s.Charset.`UTF-8`
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Transfer-Encoding`

trait CsvInstances {

  def rowEncoder[F[_]](
      fullRows: Boolean = false,
      separator: Char = ',',
      newline: String = "\n",
      escape: EscapeMode = EscapeMode.Auto,
  )(implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Stream[F, Row]] =
    csvEncoderForPipe(encodeWithoutHeaders[Row](fullRows, separator, newline, escape))

  def csvEncoderForPipe[F[_], T](pipe: Pipe[F, T, String])(implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Stream[F, T]] =
    EntityEncoder.encodeBy(
      Headers(
        `Content-Type`(MediaType.text.csv).withCharset(charset),
        `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
      )
    )(values => Entity(values.through(pipe).through(fs2.text.encode[F](charset.nioCharset))))

  def rowDecoder[F[_]](
      separator: Char = ',',
      quoteHandling: QuoteHandling = QuoteHandling.RFCCompliant,
  )(implicit F: Concurrent[F]): EntityDecoder[F, Stream[F, Row]] =
    csvDecoderForPipe(decodeWithoutHeaders[Row](separator, quoteHandling))

  def csvDecoderForPipe[F[_], T](
      pipe: Pipe[F, String, T]
  )(implicit F: Concurrent[F]): EntityDecoder[F, Stream[F, T]] =
    EntityDecoder.decodeBy(MediaType.text.csv) { msg =>
      DecodeResult.successT(
        msg.bodyText
          .through(pipe)
          .adaptError { case ex: CsvException =>
            val line = ex.line.foldMap(l => s" in line $l")
            MalformedMessageBodyFailure(s"Invalid CSV (${ex.getMessage})$line", Some(ex))
          }
      )
    }

}
