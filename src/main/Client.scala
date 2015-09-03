import org.nlogo.api.ExtensionException

import java.util.concurrent.TimeoutException

import scala.collection.mutable.{ HashMap => MutableMap }

import Sockets.MappableSocket

import Messages._

class Client(socketManager: SocketManager) {
  def requestSocket(address: String) =
    socketManager.reqSocket(address, {
      s =>
        s.setSendTimeOut(3000)
        s.setReceiveTimeOut(3000)
    })

  def rawRequest(address: String, reporter: String): String = {
    val reqSocket = requestSocket(address)
    try {
      sendRequest(reqSocket, reporter)
      reqSocket.recv().getOrElse(throw new TimeoutException("response timeout"))
    } finally {
      reqSocket.close()
    }
  }

  def request(address: String, req: Request): Response = {
    val rawResponse = rawRequest(address, Request.toString(req))
    Response.fromString(rawResponse)
      .getOrElse(throw new ExtensionException("DNL Unrecognized message: " + rawResponse))
  }

  def multiRequest(addresses: Seq[String], req: Request): Seq[Response] = {
    val results = new MutableMap[String, Response]()
    val addressToSocketMap = addresses.map(a => (a, requestSocket(a))).toMap

    val poller = addressToSocketMap.foldLeft(socketManager.poller) {
      case (poller, (address, socket)) =>
        poller.withRegistration(socket, { () =>
          results += (address -> Response.fromString(socket.recv().get).get)
        })
    }

    addressToSocketMap.values.foreach(socket => sendRequest(socket, Request.toString(req)))

    try {
      while (results.size < addressToSocketMap.size) {
        poller.poll(50)
      }
      results.values.toSeq
    } finally {
      addressToSocketMap.values.foreach(socket => socket.close())
    }
  }

  private def sendRequest(socket: MappableSocket[String, String], reporter: String): Unit = {
    if (! socket.send(reporter)) {
      throw new TimeoutException("unable to connect")
    }
  }
}
