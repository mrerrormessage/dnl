package org.nlogo.extensions.dnl

import
  org.zeromq.{ ZMQ, ZMQException },
    ZMQ.{ Socket => ZMQSocket }

object Sockets {
  class BindException(message: String) extends RuntimeException(message)

  def fromZMQBytes(bytes: Array[Byte]): String =
    new String(bytes, 0, bytes.length - 1)

  def toZMQBytes(s: String): Array[Byte] = {
    val newString = (s + " ").getBytes
    newString(newString.length - 1) = 0
    newString
  }

  def sendString(socket: ZMQSocket, s: String): Boolean =
    socket.send(toZMQBytes(s), 0)

  def recvString(socket: ZMQSocket, flags: Int = 0): Option[String] =
    Option(socket.recv(0)).map(fromZMQBytes)

  trait SocketLifecycle {
    def socket: ZMQSocket
    def prepareSend(): Unit = {}
    def send(a: Array[Byte], flags: Int = 0): Boolean = socket.send(a, flags)
    def prepareReceive(): Unit = {}
    def recv(flags: Int = 0): Option[Array[Byte]] = Option(socket.recv(flags))
    def close(): Unit = socket.close()
  }

  class ReqSocketLifecycle(val socket: ZMQSocket, address: String) extends SocketLifecycle {
    override def prepareSend(): Unit =
      socket.connect(address)
  }

  class RepSocketLifecycle(val socket: ZMQSocket, address: String) extends SocketLifecycle {
    override def prepareReceive(): Unit =
      try {
        socket.bind(address)
      } catch {
        case z: ZMQException =>
          close()
          throw new BindException("Unable to bind to " + address)
      }
  }

  trait SenderSocket[A] {
    def socket: SocketLifecycle
    def transformSend: A => Array[Byte]
    def send(a: A): Boolean = {
      socket.prepareSend()
      socket.send(transformSend(a))
    }
  }

  trait ReceiverSocket[A] {
    def socket: SocketLifecycle
    def transformRecv: Array[Byte] => A
    def recv(): Option[A] =
      socket.recv().map(transformRecv)
  }

  class MappableSocket[A, B](
    val socket: SocketLifecycle,
    override val transformRecv: Array[Byte] => A,
    override val transformSend: B => Array[Byte])
    extends ReceiverSocket[A] with SenderSocket[B] {
    def mapSend[C](f: C => B): MappableSocket[A, C] =
      new MappableSocket(
        socket,
        transformRecv,
        transformSend compose f)

    def mapRecv[C](f: A => C): MappableSocket[C, B] =
      new MappableSocket(
        socket,
        f compose transformRecv,
        transformSend)

    def close() = socket.close()
  }

  class BindableSocket[A, B](
    override val socket: RepSocketLifecycle,
    val address: String,
    transformRecv: Array[Byte] => A,
    transformSend: B => Array[Byte]) extends
  MappableSocket[A, B](socket, transformRecv, transformSend) {
    def bind() = socket.prepareReceive()
  }

  case class DNLPoller(
    protected val openPoller: Int => ZMQ.Poller,
    protected val callbackMap: Map[Int, () => Unit] = Map(),
    protected val registrations: Seq[ZMQ.Poller => Unit] = Seq()) {

    def withRegistration(sock: ReceiverSocket[_], callback: () => Unit): DNLPoller = {
      copy(
        callbackMap = callbackMap + (registrations.length -> callback),
        registrations =
          registrations :+ { (p: ZMQ.Poller) => p.register(sock.socket.socket, ZMQ.Poller.POLLIN); () })
    }

    def poll(timeout: Int) = {
      val poller = openPoller(registrations.length)
      registrations.foreach(_(poller))
      val inCount = poller.poll(timeout)
      if (inCount > 0)
        callbackMap.foreach {
          case (i, callback) => if (poller.pollin(i)) callback()
        }
    }
  }


}
