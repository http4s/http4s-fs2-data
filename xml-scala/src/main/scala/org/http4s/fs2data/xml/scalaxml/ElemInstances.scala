/*
 * Copyright 2022 http4s.org
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
package scalaxml

import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
import fs2.data.xml.dom
import fs2.data.xml.scalaXml._
import fs2.data.xml.XmlException
import org.http4s.Charset.`UTF-8`

import scala.xml.Document
import scala.xml.Elem

trait ElemInstances extends XmlEventInstances {

  implicit def xmlElemEncoder[F[_]](implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Elem] =
    xmlDocumentEncoder[F].contramap[Elem] { e =>
      ScalaXmlBuilder.makeDocument(Some("1.0"), Some(charset.renderString), None, None, Nil, e, Nil)
    }

  implicit def xmlDocumentEncoder[F[_]](implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Document] =
    xmlDocumentStreamEncoder[F].contramap[Document](Stream.emit)

  implicit def xmlDocumentStreamEncoder[F[_]](implicit
      charset: Charset = `UTF-8`
  ): EntityEncoder[F, Stream[F, Document]] =
    xmlEventsEncoder[F].contramap(dom.eventify[F, Document])

  implicit def xmlDocumentDecoder[F[_]: Concurrent]: EntityDecoder[F, Document] =
    xmlDocumentDecoder(true)

  def xmlDocumentDecoder[F[_]: Concurrent](includeComments: Boolean): EntityDecoder[F, Document] =
    xmlDocumentStreamDecoder(includeComments).flatMapR[Document] { docs =>
      DecodeResult(
        docs.head.compile.last
          .flatMap(_.liftTo[F](MalformedMessageBodyFailure("No XML document found", None)))
          .attemptNarrow[DecodeFailure]
      )
    }

  /** Handles a message body as a stream of XML documents.
    *
    * @return a stream of XML [[scala.xml.Document]]
    */
  implicit def xmlDocumentStreamDecoder[F[_]: Concurrent]: EntityDecoder[F, Stream[F, Document]] =
    xmlDocumentStreamDecoder(includeComments = true)

  /** Handles a message body as XML.
    *
    * @return an XML [[scala.xml.Document]]
    */
  def xmlDocumentStreamDecoder[F[_]](
      includeComments: Boolean
  )(implicit F: Concurrent[F]): EntityDecoder[F, Stream[F, Document]] =
    xmlEventsDecoder(includeComments).map { events =>
      events
        .through(fs2.data.xml.dom.documents)
        .adaptError { case ex: XmlException =>
          MalformedMessageBodyFailure("Invalid XML", Some(ex))
        }
    }
}
