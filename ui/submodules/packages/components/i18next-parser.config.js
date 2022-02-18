// i18next-parser.config.js

module.exports = {
    contextSeparator: "_",
    // Key separator used in your translation keys

    createOldCatalogs: true,
    // Save the \_old files

    defaultNamespace: "main",
    // Default namespace used in your i18next config

    defaultValue: "",
    // Default value to give to empty keys
    // You may also specify a function accepting the locale, namespace, and key as arguments

    indentation: 2,
    // Indentation of the catalog files

    keepRemoved: false,
    // Keep keys from the catalog that are no longer in code

    keySeparator: ".",
    // Key separator used in your translation keys
    // If you want to use plain english keys, separators such as `.` and `:` will conflict. You might want to set `keySeparator: false` and `namespaceSeparator: false`. That way, `t('Status: Loading...')` will not think that there are a namespace and three separator dots for instance.

    // see below for more details
    lexers: {
        js: ["JsxLexer"], // if you're writing jsx inside .js files, change this to JsxLexer
        ts: ["JavascriptLexer"],
        jsx: ["JsxLexer"],
        tsx: ["JsxLexer"],
        default: ["JavascriptLexer"],
    },

    lineEnding: "auto",
    // Control the line ending. See options at https://github.com/ryanve/eol

    locales: ["en"],
    // An array of the locales in your applications

    namespaceSeparator: ":",
    // Namespace separator used in your translation keys
    // If you want to use plain english keys, separators such as `.` and `:` will conflict. You might want to set `keySeparator: false` and `namespaceSeparator: false`. That way, `t('Status: Loading...')` will not think that there are a namespace and three separator dots for instance.

    output: "translations/$LOCALE/$NAMESPACE.json",
    // Supports $LOCALE and $NAMESPACE injection
    // Supports JSON (.json) and YAML (.yml) file formats
    // Where to write the locale files relative to process.cwd()

    pluralSeparator: "_",
    // Plural separator used in your translation keys
    // If you want to use plain english keys, separators such as `_` might conflict. You might want to set `pluralSeparator` to a different string that does not occur in your keys.

    input: ["src/**/*.{js,ts,tsx}"],
    // An array of globs that describe where to look for source files
    // relative to the location of the configuration file

    sort: true,
    // Whether or not to sort the catalog

    skipDefaultValues: false,
    // Whether to ignore default values
    // You may also specify a function accepting the locale and namespace as arguments

    useKeysAsDefaultValue: false,
    // Whether to use the keys as the default value; ex. "Hello": "Hello", "World": "World"
    // This option takes precedence over the `defaultValue` and `skipDefaultValues` options
    // You may also specify a function accepting the locale and namespace as arguments

    verbose: false,
    // Display info about the parsing including some stats

    failOnWarnings: false,
    // Exit with an exit code of 1 on warnings

    customValueTemplate: null,
    // If you wish to customize the value output the value as an object, you can set your own format.
    // ${defaultValue} is the default value you set in your translation function.
    // Any other custom property will be automatically extracted.
    //
    // Example:
    // {
    //   message: "${defaultValue}",
    //   description: "${maxLength}", // t('my-key', {maxLength: 150})
    // }
};
