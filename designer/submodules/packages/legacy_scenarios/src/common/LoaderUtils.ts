export function loadSvgContent(fileName: string) {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    return require(`!raw-loader!../assets/img/${fileName}`).default;
}
