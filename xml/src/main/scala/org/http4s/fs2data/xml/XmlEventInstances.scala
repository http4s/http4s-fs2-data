/*
 * Copyright 2014 http4s.org
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
package fs2data
package xml

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Chunk
import fs2.Pull
import fs2.Stream
import fs2.data.xml.render
import fs2.data.xml.XmlEvent
import fs2.text.decodeWithCharset
import org.http4s.Charset.`UTF-8`
import org.http4s.headers.{`Content-Type`, `Transfer-Encoding`}

import scala.util.matching.Regex

trait XmlEventInstances {

  implicit def xmlEventsEncoder[F[_]](implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Stream[F, XmlEvent]] =
    EntityEncoder.encodeBy(
      Headers(
        `Content-Type`(MediaType.application.xml).withCharset(charset),
        `Transfer-Encoding`(TransferCoding.chunked.pure[NonEmptyList]),
      )
    )(events =>
      Entity(
        events
          .through(render())
          .through(fs2.text.encode[F](charset.nioCharset))
      )
    )

  implicit def xmlEventsDecoder[F[_]: Concurrent]: EntityDecoder[F, Stream[F, XmlEvent]] =
    xmlEventsDecoder(true)

  def xmlEventsDecoder[F[_]](
      includeComments: Boolean
  )(implicit F: Concurrent[F]): EntityDecoder[F, Stream[F, XmlEvent]] =
    EntityDecoder.decodeBy(MediaType.text.xml, MediaType.text.html, MediaType.application.xml) {
      msg =>
        DecodeResult.successT(
          msg.body.pull
            .unconsN(64, allowFewer = true)
            // look at the first bytes to check for BOM or encoding spec in the prolog
            .flatMap {
              case Some((chunk, tail)) if chunk.take(2) === `BOM-BE` =>
                tail
                  .cons(chunk.drop(2)) // drop BOM
                  .through(decodeWithCharset(Charset.`UTF-16BE`.nioCharset))
                  .pull
                  .echo
              case Some((chunk, tail)) if chunk.take(2) === `BOM-LE` =>
                tail
                  .cons(chunk.drop(2)) // drop BOM
                  .through(decodeWithCharset(Charset.`UTF-16LE`.nioCharset))
                  .pull
                  .echo
              case Some((chunk, tail)) if msg.charset.isEmpty =>
                // guard: xml prolog is only authoritative if content type is missing, see RFC 7303 §8.8
                guessCharset(new String(chunk.toArray, `UTF-8`.nioCharset))
                  .fold(
                    Pull.raiseError[F](_),
                    charset =>
                      tail.cons(chunk).through(decodeWithCharset(charset.nioCharset)).pull.echo,
                  )
              case Some((chunk, tail)) =>
                tail
                  .cons(chunk)
                  .through(decodeWithCharset(msg.charset.getOrElse(`UTF-8`).nioCharset))
                  .pull
                  .echo
              case None => Pull.done
            }
            .stream
            .through(fs2.data.xml.events(includeComments))
        )
    }

  // Extract the encoding from the XML prolog if present. Attribute order is fixed for prologs by the spec
  // and additional whitespace is not allowed, so this strict regex should work reliably.
  private val prologWithEncoding: Regex =
    """<\?xml version=['"].+['"] encoding=['"]([^"']+)['"].*""".r

  private lazy val `BOM-BE` = Chunk(0xfe.toByte, 0xff.toByte)
  private lazy val `BOM-LE` = Chunk(0xff.toByte, 0xfe.toByte)

  private[xml] def guessCharset(in: String): ParseResult[Charset] = in match {
    case prologWithEncoding(encoding) => Charset.fromString(encoding)
    case _ => Right(Charset.`UTF-8`)
  }
}
