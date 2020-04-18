wartremoverErrors += Wart.custom("scalikejdbc.warts.ScalikejdbcDefaultZoneId")

wartremoverDependencies += "com.github.xuwei-k" %% "wartremover-scalikejdbc" % sys.props("wartremover-scalikejdbc.version")
libraryDependencies += "org.scalikejdbc" %% "scalikejdbc" % sys.props("scalikejdbc.version")
