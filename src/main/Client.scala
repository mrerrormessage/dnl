import org.nlogo.api.ExtensionException

import java.util.concurrent.TimeoutException

import scala.collection.mutable.{ HashMap => MutableMap }

import Sockets.MappableSocket

import Messages._

class Client(socketManager: SocketManager) {
  private def requestSocket(address: String) =
    socketManager.reqSocket(address, {
      s =>
        s.setSendTimeOut(3000)
        s.setReceiveTimeOut(3000)
    })

  private def messageSocket(address: String) =
    requestSocket(address).mapSend(Request.toString).mapRecv(toResponse)

  private def runRequestReply[A, B](message: B, createdSocket: => MappableSocket[A, B]): A = {
    val sock = createdSocket
    try {
      sendRequest(sock, message)
      receiveResponse(sock)
    } finally {
      sock.close()
    }
  }

  def request(address: String, req: Request): Response =
    runRequestReply(req, messageSocket(address))

  def rawRequest(address: String, reqString: String): String =
    runRequestReply(reqString, requestSocket(address))

  def multiRequest(addresses: Seq[String], req: Request): Seq[Response] = {
    val results = new MutableMap[String, Response]()
    val addressToSocketMap = addresses.map(a => (a, messageSocket(a))).toMap

    val poller = addressToSocketMap.foldLeft(socketManager.poller) {
      case (poller, (address, socket)) =>
        poller.withRegistration(socket, { () =>
          results += (address -> receiveResponse(socket))
        })
    }

    addressToSocketMap.values.foreach(socket => sendRequest(socket, req))

    try {
      while (results.size < addressToSocketMap.size) {
        poller.poll(50)
      }
      results.values.toSeq
    } finally {
      addressToSocketMap.values.foreach(socket => socket.close())
    }
  }

  private def toResponse(s: String): Response = {
    Response.fromString(s)
      .getOrElse(throw new ExtensionException("DNL Unrecognized message: " + s))
  }

  private def sendRequest[A](socket: MappableSocket[_, A], req: A): Unit =
    if (! socket.send(req))
      throw new TimeoutException("unable to connect")

  private def receiveResponse[A](socket: MappableSocket[A, _]): A =
    socket.recv().getOrElse(throw new TimeoutException("response timeout"))
}
