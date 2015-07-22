name := "DistributedSystem"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-remote" % "2.3.7"
)

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"