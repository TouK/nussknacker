
//tryStringify and tryParse are hacky functions.
// They should be removed as soon as friendly UI components for complex input objects are ready

export function tryStringify(input) {
  return _.isString(input) ? input :
    _.isObject(input) ? JSON.stringify(input) :
      input;
}

export function tryParse(input){
  try {
    return JSON.parse(input)
  } catch(e) {
    return input
  }
}