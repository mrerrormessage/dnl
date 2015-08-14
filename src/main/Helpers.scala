trait Helpers {
  def fromZMQBytes(bytes: Array[Byte]): String =
    new String(bytes, 0, bytes.length - 1)

  def toZMQBytes(s: String): Array[Byte] = {
    val newString = (s + " ").getBytes
    newString(newString.length - 1) = 0
    newString
  }
}
