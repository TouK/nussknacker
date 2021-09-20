const fs = require("fs")
const childProcess = require("child_process")

let version

if (process.env.BUILD_VERSION) {
  version = process.env.BUILD_VERSION
} else {
  const [, value] = fs.readFileSync("../../version.sbt").toString().split(":=")
  const GIT_BRANCH = childProcess.execSync("git rev-parse --abbrev-ref HEAD").toString().trim()
  const GIT_HASH = childProcess.execSync("git log -1 --format=%H").toString().trim()
  const GIT_DATE = childProcess.execSync("git log -1 --format=%cs").toString().trim()
  version = value.trim().slice(1, -1).replace("-SNAPSHOT", `-${GIT_BRANCH}-${GIT_DATE}-${GIT_HASH}-SNAPSHOT`)
}

module.exports = version
