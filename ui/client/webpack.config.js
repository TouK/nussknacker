/* eslint-disable i18next/no-literal-string */
const path = require("path")
const webpack = require("webpack")
const childProcess = require("child_process")
const HtmlWebpackPlugin = require("html-webpack-plugin")
const TerserPlugin = require("terser-webpack-plugin")
const CopyPlugin = require("copy-webpack-plugin")
const ForkTsCheckerWebpackPlugin = require("fork-ts-checker-webpack-plugin")

const NODE_ENV = process.env.NODE_ENV || "development"
const GIT_HASH = childProcess.execSync("git log -1 --format=%H").toString()
const GIT_DATE = childProcess.execSync("git log -1 --format=%cd").toString()
const isProd = NODE_ENV === "production"

const entry = {
  main: [
    "@babel/polyfill",
    path.resolve(__dirname, "./index.js"),
  ],
}

let previouslyPrintedPercentage = 0

if (!isProd) {
  entry["developer-tools"] = [
    "webpack-dev-server/client?http://localhost:3000",
    "react-hot-loader/patch",
  ]
}

const cssLoaders = [
  "style-loader",
  {
    loader: "css-loader",
    options: {
      modules: {
        mode: "global",
        localIdentName: "[name]--[local]--[hash:base64:5]",
      },
      localsConvention: "camelCase",
    },
  },
  {
    loader: "postcss-loader",
    options: {
      plugins: [
        require("autoprefixer"),
      ],
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
  //JSDOM deps override
  node: {fs: "empty", child_process: "empty", net: "empty", tls: "empty"},
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
      sourceMap: true,
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
    alias: {
      "react-dom": "@hot-loader/react-dom",
      "jointjs": "jointjs/joint.mjs",
      "jointjs/dist/joint.css": "jointjs/dist/joint.css",
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
    contentBase: __dirname,
    historyApiFallback: {
      index: "/static/main.html",
    },
    hot: true,
    hotOnly: true,
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
    new HtmlWebpackPlugin({
      title: "Nussknacker",
      hash: true,
      filename: "main.html",
      template: "index_template_no_doctype.ejs",
    }),
    isProd ? null : new webpack.NamedModulesPlugin(),
    isProd ? null : new webpack.HotModuleReplacementPlugin(),
    new CopyPlugin([
      {from: "translations", to: "assets/locales"},
    ]),
    new webpack.DefinePlugin({
      __DEV__: !isProd,
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
  ].filter(p => p !== null),
  module: {
    rules: [
      {
        test: /\.html$/,
        loader: "html-loader?minimize=false",
      },
      {
        test: /\.m?[tj]sx?$/,
        use: ["babel-loader"],
        exclude: /node_modules/,
      },
      {
        test: /\.worker\.(js|ts)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: "comlink-loader",
            options: {
              singleton: true,
            },
          },
          "babel-loader",
        ],
      },
      {
        test: /\.css?$/,
        use: cssLoaders,
      },
      {
        test: /\.styl$/,
        use: [...cssLoaders, "stylus-loader"],
      },
      {
        test: /\.less$/,
        use: [...cssLoaders, "less-loader"],
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
            exclude: /node_modules/,
            issuer: {
              test: /\.[tj]sx?$/,
            },
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
