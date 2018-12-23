wartremoverErrors += Wart.custom("scalikejdbc.warts.ScalikejdbcDefaultZoneId")

libraryDependencies += "com.github.xuwei-k" %% "wartremover-scalikejdbc" % sys.props("wartremover-scalikejdbc.version")
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % sys.props("scalikejdbc.version")

wartremoverClasspaths ++= {
  (dependencyClasspath in Compile).value.files
    .find(_.name.contains("wartremover-scalikejdbc"))
    .map(_.toURI.toString)
    .toList
}
