
export function loadNodeSvgContent(fileName) {
  return require(`!raw-loader!../assets/img/nodes/${fileName}`)
}