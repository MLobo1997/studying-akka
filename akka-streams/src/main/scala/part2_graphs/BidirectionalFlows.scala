package part2_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import GraphDSL.Implicits._
import akka.stream.{BidiShape, ClosedShape}

object BidirectionalFlows extends App {
  implicit val system: ActorSystem = ActorSystem("BidirectionalFlows")

  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)
  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  println(encrypt(3)("akka"))
  println(encrypt(3)("dnnd"))

  val bidiCryptoStaticGraph = GraphDSL.create() { implicit builder =>
    val encryptionShape = builder.add(Flow[String].map(encrypt(3)))
    val decryptionShape = builder.add(Flow[String].map(decrypt(3)))

    /*
    BidiShape(
      encryptionShape.in,
      encryptionShape.out,
      decryptionShape.in,
      decryptionShape.out
    )
     */
    BidiShape.fromFlows(encryptionShape, decryptionShape)
  }

  val unencryptedStrings = List("a", "cool", "example")

  val unencryptedSource = Source(unencryptedStrings)
  val encryptedSource = Source(unencryptedStrings.map(encrypt(3)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      val unencryptedSourceShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)

      val bidi = builder.add(bidiCryptoStaticGraph)
      val encryptedSink =
        builder.add(Sink.foreach[String](str => println(s"encrypted: $str")))
      val decryptedSink =
        builder.add(Sink.foreach[String](str => println(s"decrypted: $str")))

      unencryptedSourceShape ~> bidi.in1
      bidi.out1 ~> encryptedSink

      bidi.in2 <~ encryptedSourceShape
      decryptedSink <~ bidi.out2
      /*
      encryptedSourceShape ~> bidi.in2
      bidi.out2 ~> decryptedSink
       */

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

  Thread.sleep(1000)

  system.terminate()
}
