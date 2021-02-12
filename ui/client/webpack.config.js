/* eslint-disable i18next/no-literal-string */
const bootstrap = require("bootstrap")
const path = require("path")
const webpack = require("webpack")
const childProcess = require("child_process")
const HtmlWebpackPlugin = require("html-webpack-plugin")
const TerserPlugin = require("terser-webpack-plugin")
const CopyPlugin = require("copy-webpack-plugin")
const ForkTsCheckerWebpackPlugin = require("fork-ts-checker-webpack-plugin")
const {camelCase} = require("lodash")
const MomentLocalesPlugin = require("moment-locales-webpack-plugin")
const ReactRefreshWebpackPlugin = require("@pmmmwh/react-refresh-webpack-plugin")

const NODE_ENV = process.env.NODE_ENV || "development"
const GIT_HASH = childProcess.execSync("git log -1 --format=%H").toString()
const GIT_DATE = childProcess.execSync("git log -1 --format=%cd").toString()
const isProd = NODE_ENV === "production"

const {ModuleFederationPlugin} = webpack.container
const {dependencies, name} = require("./package.json")
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

module.exports = {
  mode: NODE_ENV,
  optimization: {
    splitChunks: {
      cacheGroups: {
        commons: {
          test: /[\\/]node_modules[\\/]/,
          name: "vendors",
          chunks: "all",
        },
      },
    },
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
    },
  },
  entry: entry,
  output: {
    //by default we use default webpack value, but we want to be able to override it for building frontend via sbt
    path: process.env.OUTPUT_PATH ? path.join(process.env.OUTPUT_PATH, "classes", "web", "static") : path.join(process.cwd(), "dist"),
    filename: "[name].js",
    //see config.js
    publicPath: isProd ? "__publicPath__/static/" : "/static/",
  },
  devtool: isProd ? "hidden-source-map" : "eval-source-map",
  devServer: {
    publicPath: isProd ? "__publicPath__/static/" : "/static/",
    historyApiFallback: {
      index: "/static/main.html",
    },
    hot: true,
    host: "0.0.0.0",
    disableHostCheck: true,
    port: 3000,
    proxy: {
      "/api": {
        target: process.env.BACKEND_DOMAIN,
        changeOrigin: true,
      },
      "/be-static": {
        target: process.env.BACKEND_DOMAIN,
        changeOrigin: true,
        pathRewrite: {
          "^/be-static": "/static",
        },
      },
    },
  },
  plugins: [
    new MomentLocalesPlugin({
      localesToKeep: ["pl"],
    }),
    new ModuleFederationPlugin({
      name: camelCase(name),
      shared: {
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
      filename: "main.html",
      template: "index_template_no_doctype.ejs",
    }),
    new CopyPlugin({
      patterns: [
        {from: "translations", to: "assets/locales"},
        {from: "assets/img/favicon.png", to: "assets/img/favicon.png"},
      ],
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
    // each 10% log entry in separate line - fix for travis no output problem
    new webpack.ProgressPlugin((percentage, message, ...args) => {
      const decimalPercentage = Math.ceil(percentage * 100)
      if (this.previouslyPrintedPercentage == null || decimalPercentage >= this.previouslyPrintedPercentage + 10 || decimalPercentage === 100) {
        console.log(` ${decimalPercentage}%`, message, ...args)
        this.previouslyPrintedPercentage = decimalPercentage
      }
    }),
    new ForkTsCheckerWebpackPlugin(),
    !isProd && new ReactRefreshWebpackPlugin(),
  ].filter(p => p !== null),
  module: {
    rules: [
      {
        // TODO: remove after update to babel 7.12
        // https://github.com/babel/babel/pull/10853
        // https://github.com/webpack/webpack/issues/11467#issuecomment-691873586
        test: /\.m?js/,
        resolve: {
          fullySpecified: false,
        },
      },
      {
        test: require.resolve("jointjs"),
        use: [
          {
            loader: "expose-loader",
            options: {
              exposes: ["joint"],
            },
          },
        ],
      },
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
        use: cssPreLoaders,
      },
      {
        test: /\.styl$/,
        enforce: "pre",
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
        use: [
          "svg-transform-loader",
          {
            loader: "svgo-loader",
            options: {
              externalConfig: ".svgo.yml",
            },
          },
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
