/* eslint-disable i18next/no-literal-string */
const progressBar = require("./progressBar.js")
const bootstrap = require("bootstrap")
const path = require("path")
const webpack = require("webpack")
const childProcess = require("child_process")
const HtmlWebpackPlugin = require("html-webpack-plugin")
const HtmlWebpackHarddiskPlugin = require("html-webpack-harddisk-plugin")
const TerserPlugin = require("terser-webpack-plugin")
const ForkTsCheckerWebpackPlugin = require("fork-ts-checker-webpack-plugin")
const federationConfig = require("./federation.config.json")
const MomentLocalesPlugin = require("moment-locales-webpack-plugin")
const ReactRefreshWebpackPlugin = require("@pmmmwh/react-refresh-webpack-plugin")
const PreloadWebpackPlugin = require("@vue/preload-webpack-plugin")
const WebpackShellPluginNext = require("webpack-shell-plugin-next")
const CopyPlugin = require("copy-webpack-plugin")

const NODE_ENV = process.env.NODE_ENV || "development"
const GIT_HASH = childProcess.execSync("git log -1 --format=%H").toString()
const GIT_DATE = childProcess.execSync("git log -1 --format=%cd").toString()
const isProd = NODE_ENV === "production"

const {ModuleFederationPlugin} = webpack.container
const entry = {
  main: path.resolve(__dirname, "./init.js"),
}

const cssPreLoaders = [
  {
    loader: "postcss-loader",
    options: {
      postcssOptions: {
        plugins: [
          require("autoprefixer"),
          require("postcss-move-props-to-bg-image-query"),
        ],
      },
    },
  },
]

const fileLoader = {
  loader: "file-loader",
  options: {
    name: "assets/images/[name][hash].[ext]",
  },
}

//by default we use default webpack value, but we want to be able to override it for building frontend via sbt
const outputPath = process.env.OUTPUT_PATH ?
  path.join(process.env.OUTPUT_PATH, "classes", "web", "static") :
  path.join(process.cwd(), "dist")

module.exports = {
  mode: NODE_ENV,
  optimization: {
    minimizer: [new TerserPlugin({
      parallel: true,
      //Reactable bug: https://github.com/abdulrahman-khankan/reactable/issues/3
      terserOptions: {
        mangle: {
          reserved: ["Td", "Tr", "Th", "Thead", "Table"],
        },
      },
    })],
  },
  performance: {
    maxEntrypointSize: 3000000,
    maxAssetSize: 3000000,
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx", ".json"],
    fallback: {
      path: require.resolve("path-browserify"), //reason: react-markdown
      crypto: require.resolve("crypto-browserify"), //reason: jsonwebtoken
      stream: require.resolve("stream-browserify"), //reason: jsonwebtoken
      http: require.resolve("stream-http"), //reason: matomo-tracker
      https: require.resolve("https-browserify"), //reason: matomo-tracker
      fs: false,
    },
  },
  entry: entry,
  output: {
    path: outputPath,
    filename: "[name].js",
  },
  devtool: isProd ? "hidden-source-map" : "eval-source-map",
  devServer: {
    contentBase: [
      path.join(__dirname, "dist"),
    ],
    historyApiFallback: {
      index: "/main.html",
    },
    overlay: false,
    hot: true,
    host: "0.0.0.0",
    disableHostCheck: true,
    headers: {
      "Access-Control-Allow-Credentials": "true",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
      "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization",
    },
    port: 3000,
    proxy: {
      "/api": {
        target: process.env.BACKEND_DOMAIN,
        changeOrigin: true,
        onProxyRes: (proxyRes, req) => {
          if (req.headers?.origin) {
            proxyRes.headers["Access-Control-Allow-Origin"] = req.headers.origin
          }
        },
      },
      "/be-static": {
        target: process.env.BACKEND_DOMAIN,
        changeOrigin: true,
        pathRewrite: {
          "^/be-static": "/static",
        },
      },
    },
    watchOptions: {
      ignored: [
        "webpack.config.js",
        "**/dist",
        "**/target",
        // ignore vim swap files
        "**/*.sw[pon]",
        // TODO: separate src/main, src/test and so on
        "**/cypress*",
        "**/.nyc_output",
        "**/.federated-types/**/*",
        "**/dist/*-dts.tgz",
        "**/jest*",
        "**/test*",
        "**/*.md",
      ],
    },
  },
  plugins: [
    new MomentLocalesPlugin({
      localesToKeep: ["pl"],
    }),
    new ModuleFederationPlugin({
      filename: "remoteEntry.js",
      // `federation.config.json` is used by @pixability-ui/federated-types,
      // it's also good method to connect all places where `name` is needed.
      ...federationConfig,
      shared: {
        ...require("./package.json").dependencies,
        react: {
          eager: true,
          singleton: true,
        },
        "react-dom": {
          eager: true,
          singleton: true,
        },
      },
    }),
    new HtmlWebpackPlugin({
      title: "Nussknacker",
      hash: true,
      chunks: ["runtime", "main"],
      //see ./config.ts
      base: isProd ? "__publicPath__/static/" : "/",
      filename: "main.html",
      favicon: "assets/img/favicon.png",
    }),
    new HtmlWebpackHarddiskPlugin(),
    new WebpackShellPluginNext({
      onAfterDone: {
        scripts: [
          `npx make-federated-types --outputDir .federated-types`,
          // this .tgz with types for exposed modules lands in public root
          // and could be downloaded by remote side (e.g. `webpack-remote-types-plugin`).
          `mkdir -p "${outputPath}"`,
          `tar -C .federated-types -czf "${path.join(outputPath, `${federationConfig.name}-dts.tgz`)}" .`,
          `rm -rf .federated-types`,
        ],
        swallowError: true,
      },
    }),
    new CopyPlugin({
      patterns: [
        {from: "translations", to: "assets/locales", noErrorOnMissing: true},
      ],
    }),
    new PreloadWebpackPlugin({
      rel: "preload",
      as: "font",
      include: "allAssets",
      fileWhitelist: [/\.(woff2?|eot|ttf|otf)(\?.*)?$/i],
    }),
    new PreloadWebpackPlugin({
      rel: "preload",
      as: "image",
      include: "allAssets",
      fileWhitelist: [/\.(svg)(\?.*)?$/i],
    }),
    new webpack.ProvidePlugin({
      process: "process/browser",
    }),
    new webpack.DefinePlugin({
      __DEV__: !isProd,
      "process.version": JSON.stringify(process.version), //reason: jsonwebtoken
      "process.browser": true, //reason: jsonwebtoken
      "process.env": {
        NODE_ENV: JSON.stringify(NODE_ENV),
      },
      __GIT__: {
        HASH: JSON.stringify(GIT_HASH),
        DATE: JSON.stringify(GIT_DATE),
      },
    }),
    new ForkTsCheckerWebpackPlugin(),
    isProd ? null : new ReactRefreshWebpackPlugin(),
    new webpack.ProgressPlugin(progressBar),
  ].filter(Boolean),
  module: {
    rules: [
      {
        test: /\.html$/,
        use: {
          loader: "html-loader",
          options: {
            minimize: false,
          },
        },
      },
      {
        test: /\.[tj]sx?$/,
        exclude: /node_modules/,
        use: ["babel-loader"],
      },
      {
        test: /\.(css|styl|less)?$/,
        use: [
          "style-loader",
          {
            loader: "css-loader",
            options: {
              modules: {
                mode: "global",
                exportGlobals: true,
                localIdentName: "[name]--[local]--[hash:base64:5]",
                exportLocalsConvention: "camelCase",
              },
            },
          },
        ],
      },
      {
        test: /\.css?$/,
        enforce: "pre",
        exclude: /node_modules/,
        use: cssPreLoaders,
      },
      {
        test: /\.styl$/,
        enforce: "pre",
        exclude: /node_modules/,
        use: [
          ...cssPreLoaders,
          {
            loader: "stylus-loader",
            options: {
              stylusOptions: {
                use: [bootstrap()],
              },
            },
          },
        ],
      },
      {
        test: /\.less$/,
        enforce: "pre",
        exclude: /node_modules/,
        use: [...cssPreLoaders, "less-loader"],
      },
      {
        test: /\.(eot|ttf|woff|woff2)$/,
        use: [fileLoader],
      },
      {
        test: /\.(png|jpg)$/,
        use: [fileLoader],
      },
      {
        test: /\.svg$/,
        enforce: "pre",
        exclude: /font/,
        use: [
          "svg-transform-loader",
          "svgo-loader",
        ],
      },

      {
        test: /\.svg$/,
        oneOf: [
          {
            issuer: /\.[tj]sx?$/,
            use: [
              "babel-loader",
              {
                loader: "@svgr/webpack",
                options: {
                  svgo: true,
                },
              },
              fileLoader,
            ],
          },
          {
            use: [fileLoader],
          },
        ],
      },
    ],
  },
}
