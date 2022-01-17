const fs = require("fs")
const childProcess = require("child_process")
const path = require("path")

let version

if (process.env.NUSSKNACKER_VERSION) {
  version = process.env.NUSSKNACKER_VERSION
} else {
  const [, value] = fs.readFileSync(path.join(__dirname, "../../version.sbt")).toString().split(":=")
  const GIT_BRANCH = childProcess.execSync("git rev-parse --abbrev-ref HEAD").toString().trim()
  const GIT_HASH = childProcess.execSync("git log -1 --format=%H").toString().trim()
  const GIT_DATE = childProcess.execSync("git log -1 --format=%cs").toString().trim()
  version = value.trim().slice(1, -1).replace("-SNAPSHOT", `-${GIT_BRANCH}-${GIT_DATE}-${GIT_HASH}-SNAPSHOT`)
}

// https://werxltd.com/wp/2010/05/13/javascript-implementation-of-javas-string-hashcode-method/
function hash(str) {
  var hash = 0, i, chr
  if (str.length === 0) return hash
  for (i = 0; i < str.length; i++) {
    chr = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + chr
    hash |= 0 // Convert to 32bit integer
  }
  return hash
}

module.exports = {version, hash: hash(version)}
