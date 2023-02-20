package org.http4s
package fs2data.csv

import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.{Pipe, Stream}
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
