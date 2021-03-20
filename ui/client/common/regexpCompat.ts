//Older browsers (pre ES2018) do not support 's' (dotAll) flag.
//In regex literals this is handled by https://babeljs.io/docs/en/babel-plugin-transform-dotall-regex,
//but if we construct RegExp object by hand (e.g. by concatenating) we have to use replacement
export const dotAllReplacement = "[\\s\\S]"
