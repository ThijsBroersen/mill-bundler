package io.github.nafg.millbundler

import io.github.nafg.millbundler.testcommon.BaseSuite

import mill.*
import mill.api.Discover
import mill.scalajslib.api.{ModuleKind, ModuleSplitStyle}
import mill.testkit.TestRootModule
import mill.util.TokenReaders.*

class WebpackSuite extends BaseSuite:

  test("Webpack - simple") {
    object build extends BaseBuild:
      object test extends BaseTestModule with ScalaJSWebpackModule.Test:
        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[test.type]
    end build

    checkTestResults(build.test, "webpack-simple")
  }

  test("Webpack - multi entry") {
    val inputs = Seq(multiEntryDir / "entry-a.js", multiEntryDir / "entry-b.js")

    object build extends BaseBuild:
      object bundleTest extends TestRootModule with ScalaJSWebpackModule.AsApplication:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion

        def runMultiBundle = Task {
          bundle.apply()(BundleParams(inputs, opt = false))
        }

        def runWebpackConfig = Task {
          webpackConfig()(BundleParams(inputs, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[bundleTest.type]
    end build

    val config =
      evalTask(build.bundleTest, "multi-entry", build.bundleTest.runWebpackConfig)
    assert(config.contains("[name]"))
    assert(config.contains("entry-a.js"))
    assert(config.contains("entry-b.js"))

    val bundles =
      evalTask(build.bundleTest, "multi-entry", build.bundleTest.runMultiBundle)
    val outputs = bundles.map(_.path.last).toSet
    assert(outputs.contains("out-bundle-entry-a.js"))
    assert(outputs.contains("out-bundle-entry-b.js"))
  }

  test("Webpack - empty input fails") {
    object build extends BaseBuild:
      object bundleTest extends TestRootModule with ScalaJSWebpackModule.AsApplication:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion

        def runBundle = Task {
          bundle.apply()(BundleParams(Nil, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[bundleTest.type]
    end build

    val err =
      evalTaskExpectFailure(
        build.bundleTest,
        "multi-entry",
        build.bundleTest.runBundle
      )
    assert(
      err.getMessage.contains("No input files") ||
        Option(err.getCause).exists(_.getMessage.contains("No input files"))
    )
  }

  test("Webpack - split modules") {
    object build extends BaseBuild:
      object app extends TestRootModule with ScalaJSWebpackModule.AsApplication:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion
        override def moduleKind = ModuleKind.CommonJSModule
        override def moduleSplitStyle = ModuleSplitStyle.FewestModules

        def runSplitBundle = Task {
          val report = fastLinkJS()
          val paths = getReportMainFilePath(report).toSeq
          assert(
            paths.size > 1,
            s"expected multiple public modules, got ${paths.size}: ${paths.mkString(", ")}"
          )
          bundle.apply()(BundleParams(paths, opt = false))
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[app.type]
    end build

    val bundles =
      evalTask(build.app, "webpack-split-app", build.app.runSplitBundle)
    assert(bundles.size >= 2)
  }

  test("Webpack - as library") {
    object build extends BaseBuild:
      object frontend extends TestRootModule with ScalaJSWebpackModule.AsLibrary:
        override def moduleDeps = Seq(build)
        override def scalaVersion = build.scalaVersion
        override def scalaJSVersion = build.scalaJSVersion

        def runDevBundle = Task {
          devBundle()
        }

        lazy val millDiscover = Discover[this.type]

      lazy val millDiscover = Discover[frontend.type]
    end build

    val bundles =
      evalTask(build.frontend, "webpack-library", build.frontend.runDevBundle)
    val outputNames = bundles.map(_.path.last).toSet
    assert(outputNames.contains("out-bundle.js"))
    assert(bundles.exists(_.path.ext == "js"))
  }

end WebpackSuite
