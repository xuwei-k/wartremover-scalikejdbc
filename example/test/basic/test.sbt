TaskKey[Unit]("writeTestFile1") := {
  IO.write(
    file = file("Test.scala"),
    content = """package foo

object Main {
  val x = scalikejdbc.TypeBinder.of[java.time.ZonedDateTime]
}
"""
  )
}
