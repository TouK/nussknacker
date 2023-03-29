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
            onlyMatchedOnce: true,
          },
          removeUselessStrokeAndFill: {
            removeNone: true,
          },
          removeViewBox: false,
        },
      },
    },
    "removeOffCanvasPaths",
    "convertStyleToAttrs",
    {
      name: "removeAttrs",
      params: {
        attrs: "(,*vectornator.*)",
      },
    },
    {
      name: "convertColors",
      params: {
        currentColor: /#ccc|#fff/,
      },
    },
  ],
}

