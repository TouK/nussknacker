module.exports = {
  multipass: true,
  pretty: true,
  plugins: [
    {
      name: "preset-default",
      params: {
        overrides: {
          //We inline styles because otherwise those styles are globally visible and can cause some unexpected behaviour
          inlineStyles: {
            onlyMatchedOnce: false,
          },
          removeUselessStrokeAndFill: {
            removeNone: true,
          },
        },
      },
    },
    "removeOffCanvasPaths",
    {
      name: "removeAttrs",
      params: {
        attrs: "(,*vectornator.*)",
      },
    },
  ],
}

