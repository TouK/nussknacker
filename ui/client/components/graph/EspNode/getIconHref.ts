import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import "../graphTheme.styl"

export function getIconHref(node, nodesSettings) {
  const iconFromConfig = nodesSettings?.icon
  const defaultIconName = `${node.type}.svg`
  return absoluteBePath(`/assets/nodes/${iconFromConfig || defaultIconName}`)
}
