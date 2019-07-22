import babel from 'rollup-plugin-babel'
import commonjs from 'rollup-plugin-commonjs'
import external from 'rollup-plugin-peer-deps-external'
import postcss from 'rollup-plugin-postcss'
import resolve from 'rollup-plugin-node-resolve'
import url from 'rollup-plugin-url'
import replace from 'rollup-plugin-replace'
import globals from 'rollup-plugin-node-globals';

import svgr from '@svgr/rollup'


import pkg from './package.json'

export default {
  input: 'src/index.js',
  output: [
    {
      file: pkg.main,
      format: 'iife',
      sourcemap: true,
      external: [ 'react', 'react-dom', 'styled-components'],
      globals: {
        'styled-components': 'styled',
        react: 'React',
        'react-dom': 'ReactDOM',
        'PluginManager': 'PluginManager'
      }
    }
  ],
  plugins: [
    external(),
    postcss({
      modules: true,
      extensions: ['.css', '.scss', '.less'],
      use : [
        'sass',
        ['less', { javascriptEnabled: true }]
      ]
    }),
    url(),
    svgr(),
    babel({
      exclude: 'node_modules/**',
      plugins: [ 'external-helpers' ]
    }),
    resolve(),
    commonjs({
      include: 'node_modules/**',
      namedExports: {
        'node_modules/react-is/index.js': ['isValidElementType']
      }
    }),
    /*globals(),
    replace({
      'process.env.NODE_ENV': JSON.stringify( 'production' )
    }) */
  ]
}
