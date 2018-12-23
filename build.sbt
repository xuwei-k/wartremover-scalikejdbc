import scala.collection.JavaConverters._
import java.lang.management.ManagementFactory
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Git

val Scala211 = "2.11.12"
val Scala212 = "2.12.8"
val Scala213 = "2.13.0-M5"

val scalikejdbcVersion = "3.3.2"
val wartremoverVersion = "2.3.7"

val projectName = "wartremover-scalikejdbc"

val updateReadmeTask = { state: State =>
  val extracted = Project.extract(state)
  val v = extracted get version
  val org = extracted get organization
  val modules = projectName :: Nil
  val readme = "README.md"
  val readmeFile = file(readme)
  val newReadme = Predef
    .augmentString(IO.read(readmeFile))
    .lines
    .map { line =>
      val matchReleaseOrSnapshot = line.contains("SNAPSHOT") == v.contains("SNAPSHOT")
      if (line.startsWith("libraryDependencies") && matchReleaseOrSnapshot) {
        val i = modules.map("\"" + _ + "\"").indexWhere(line.contains)
        s"""libraryDependencies += "$org" %% "${modules(i)}" % "$v""""
      } else {
        line
      }
    }
    .mkString("", "\n", "\n")
  IO.write(readmeFile, newReadme)
  val git = new Git(extracted get baseDirectory)
  git.add(readme) ! state.log
  git.commit(message = "update " + readme, sign = false, signOff = false) ! state.log
  sys.process.Process("git diff HEAD^") ! state.log
  state
}

val updateReadmeProcess: ReleaseStep = updateReadmeTask

val tagName = Def.setting {
  s"v${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}"
}

val tagOrHash = Def.setting {
  if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
  else tagName.value
}

val unusedWarnings = Seq("-Ywarn-unused")

lazy val core = project
  .in(file("core"))
  .settings(
    name := projectName,
    crossScalaVersions := Seq(Scala211, Scala212, Scala213),
    libraryDependencies ++= Seq(
      "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcVersion % "test",
      "com.novocode" % "junit-interface" % "0.11" % "test",
      "org.wartremover" %% "wartremover" % wartremoverVersion,
    ),
    commonSettings,
  )

lazy val tests = project
  .in(file("tests"))
  .enablePlugins(ScriptedPlugin)
  .settings(
    commonSettings,
    noPublish,
    sbtTestDirectory := file("example"),
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= ManagementFactory.getRuntimeMXBean.getInputArguments.asScala.toList.filter(
      a => Seq("-Xmx", "-Xms", "-XX", "-Dsbt.log.noformat").exists(a.startsWith)
    ),
    scriptedLaunchOpts ++= Seq(
      s"-Dscalikejdbc.version=${scalikejdbcVersion}",
      s"-Dwartremover.version=${wartremoverVersion}",
      s"-Dwartremover-scalikejdbc.version=${version.value}"
    ),
  )

lazy val noPublish = Def.settings(
  skip in publish := true,
  PgpKeys.publishLocalSigned := {},
  PgpKeys.publishSigned := {},
  publishLocal := {},
  publish := {},
  publishArtifact in Compile := false
)

noPublish
commonSettings

lazy val commonSettings = Def.settings(
  unmanagedResources in Compile += (baseDirectory in LocalRootProject).value / "LICENSE.txt",
  scalaVersion := Scala212,
  scalacOptions ++= unusedWarnings,
  Seq(Compile, Test).flatMap(c => scalacOptions in (c, console) --= unusedWarnings),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:existentials",
  ),
  description := "warts for scalikejdbc",
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  organization := "com.github.xuwei-k",
  pomExtra in Global := {
    <url>https://github.com/xuwei-k/wartremover-scalikejdbc</url>
      <scm>
        <connection>scm:git:github.com/xuwei-k/wartremover-scalikejdbc.git</connection>
        <developerConnection>scm:git:git@github.com:xuwei-k/wartremover-scalikejdbc.git</developerConnection>
        <url>github.com/xuwei-k/wartremover-scalikejdbc.git</url>
        <tag>{tagOrHash.value}</tag>
      </scm>
      <developers>
        <developer>
          <id>xuwei-k</id>
          <name>Kenji Yoshida</name>
          <url>https://github.com/xuwei-k</url>
        </developer>
      </developers>
  },
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  scalacOptions in (Compile, doc) ++= {
    val t = tagOrHash.value
    Seq(
      "-sourcepath",
      (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url",
      s"https://github.com/xuwei-k/wartremover-scalikejdbc/tree/${t}€{FILE_PATH}.scala"
    )
  },
  ReleasePlugin.extraReleaseCommands,
  commands += Command.command("updateReadme")(updateReadmeTask),
  releaseTagName := tagName.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    updateReadmeProcess,
    tagRelease,
    ReleaseStep(
      action = { state =>
        val extracted = Project extract state
        extracted.runAggregated(PgpKeys.publishSigned in Global in extracted.get(thisProjectRef), state)
      },
      enableCrossBuild = true
    ),
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    updateReadmeProcess,
    pushChanges
  )
)
