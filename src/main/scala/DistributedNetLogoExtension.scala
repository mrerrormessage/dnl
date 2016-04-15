package org.nlogo.extensions.dnl

import java.util.concurrent.TimeoutException

import java.net.{ Inet4Address, NetworkInterface }

import Messages._

import org.nlogo.agent.Observer
import org.nlogo.api.{ Argument, Command, Context,
  Dump, DefaultClassManager, ExtensionException, ExtensionManager,
  NetLogoLegacyDialect, PrimitiveManager, Reporter, SimpleJobOwner }
import org.nlogo.core.{ AgentKind, LogoList, Syntax }
import org.nlogo.api.ScalaConversions._
import org.nlogo.app.App
import org.nlogo.compiler.Compiler

import org.zeromq.ZMQ, ZMQ.{ Context => ZMQContext }

import scala.util.Random
import scala.collection.JavaConversions._

class DistributedNetLogoExtension extends DefaultClassManager {
  var context       = Option.empty[ZMQContext]
  var server        = Option.empty[ServerThread]

  override def load(manager: PrimitiveManager) = {
    val port = 9000 + Random.nextInt(100)

    context = Some(ZMQ.context(1))
    val ipAddress = networkAddress.getOrElse("127.0.0.1")
    val address = "tcp://" + ipAddress + ":" + port.toString
    val socketManager = new SocketManager(context.get)
    server = context.map(ctx => new ServerThread(socketManager, address, serveNetLogo))
    val client = new Client(socketManager)

    manager.addPrimitive("info",         new Info(address))
    manager.addPrimitive("report",       new Report(client))
    manager.addPrimitive("command",      new DnlCommand(client))
    manager.addPrimitive("command-sync", new BlockingCommand(client))

    server.map(_.start())
  }

  override def unload(em: ExtensionManager) = {
    server.foreach {
      t =>
        t.close()
        t.join(10000)
    }
    server = None
    context.foreach(_.close())
    context = None
  }

  private def networkAddress: Option[String] = {
    val addresses = for {
      iface   <- NetworkInterface.getNetworkInterfaces
      address <- iface.getInetAddresses if address.isInstanceOf[Inet4Address] && ! address.isLoopbackAddress
    } yield address.getHostAddress

    addresses.toList.headOption
  }

  private def serveNetLogo(m: Request): Response = {
    val workspace = App.app.workspace
    val jobOwner = new SimpleJobOwner("DNL", workspace.world.mainRNG, AgentKind.Observer)
    m match {
      case Reporter(rep) =>
        val compiledReporter = workspace.compileReporter(rep)
        val reporterResult = workspace.runCompiledReporter(jobOwner, compiledReporter)
        LogoObject(Dump.logoObject(reporterResult, true, true))
      case SyncCommand(cmd) =>
        val compiledCommand = workspace.compileCommands(cmd)
        workspace.runCompiledCommands(jobOwner, compiledCommand)
        CommandComplete(cmd)
      case AsyncCommand(cmd) =>
        val compiledCommand = workspace.compileCommands(cmd)
        val job = workspace.jobManager.makeConcurrentJob(
          jobOwner, workspace.world.observers, compiledCommand)
        workspace.jobManager.addJob(job, waitForCompletion = false)
        CommandComplete(cmd)
    }
  }
}

class Info(address: String) extends Reporter {
  override def getSyntax: Syntax = Syntax.reporterSyntax(ret = Syntax.StringType)

  override def report(args: Array[Argument], context: Context): AnyRef =
    address
}

trait ClientProcedure {
  def client: Client

  val defaultResponseHandler: PartialFunction[Response, Nothing] = {
    case LogoObject(lodump) =>
      throw new ExtensionException("DNL Internal error: expected command result, got reporter result " + lodump)
    case ExceptionResponse(message) =>
      throw new ExtensionException("DNL Remote Exception: " + message)
    case InvalidMessage(message) =>
      throw new ExtensionException("DNL Internal error: " + message)
    case CommandComplete(cmd) =>
      throw new ExtensionException("DNL Internal error: expected reporter result, got command result " + cmd)
  }

  def clientRequest[T <: AnyRef](arg: Argument, req: Request)(handler: PartialFunction[Response, T]): AnyRef = {
    try {
      arg.get match {
        case address:   String   => (handler orElse defaultResponseHandler).apply(client.request(address, req))
        case addresses: LogoList =>
          val validAddresses: Seq[String] =
            addresses.scalaIterator.collect { case s: String => s }.toSeq
          val responses =
            client.multiRequest(validAddresses, req).map(handler orElse defaultResponseHandler)
          LogoList.fromIterator(responses.toIterator)
      }
    } catch {
      case e: TimeoutException =>
        throw new ExtensionException("DNL Timeout: " + e.getMessage)
    }
  }
}

class Report(val client: Client) extends Reporter with ClientProcedure {
  override def getSyntax: Syntax =
    Syntax.reporterSyntax(right = List(Syntax.StringType | Syntax.ListType, Syntax.CodeBlockType), ret = Syntax.WildcardType)

  override def report(args: Array[Argument], context: Context): AnyRef = {
    val reporter = args(1).getCode
    clientRequest(args(0), Reporter(reporter.map(_.text).mkString(" "))) {
      case LogoObject(lodump) =>
        // no 3D support (for now)
        new Compiler(NetLogoLegacyDialect).readFromString(lodump)
    }
  }
}

class DnlCommand(val client: Client) extends Command with ClientProcedure {
  override def getSyntax: Syntax =
    Syntax.commandSyntax(right = List(Syntax.StringType | Syntax.ListType, Syntax.CodeBlockType))

  override def perform(args: Array[Argument], context: Context): Unit = {
    val command = args(1).getCode
    clientRequest(args(0), AsyncCommand(command.map(_.text).mkString(" "))) {
      case CommandComplete(cmd) => None
    }
  }
}

class BlockingCommand(val client: Client) extends Command with ClientProcedure {
  override def getSyntax: Syntax =
    Syntax.commandSyntax(right = List(Syntax.StringType | Syntax.ListType, Syntax.CodeBlockType))

  override def perform(args: Array[Argument], context: Context): Unit = {
    val command = args(1).getCode
    clientRequest(args(0), SyncCommand(command.map(_.text).mkString(" "))) {
      case CommandComplete(cmd) => None
    }
  }
}
