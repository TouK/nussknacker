export function loadSvgContent(fileName) {
  return require(`!raw-loader!../assets/img/${fileName}`).default
}
