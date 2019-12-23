
export function loadNodeSvgContent(fileName) {
  return loadSvgContent(`nodes/${fileName}`)
}

export function loadSvgContent(fileName) {
  return require(`!raw-loader!../assets/img/${fileName}`)
}
