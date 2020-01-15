import Flattenizer from "flattenizer"
import _ from "lodash"

//tryStringify and tryParse are hacky functions.
// They should be removed as soon as friendly UI components for complex input objects are ready

export function tryStringify(input) {
  return _.isString(input) ? input : _.isObject(input) ? JSON.stringify(input) : input
}

export function tryParse(input){
  try {
    return JSON.parse(input)
  } catch(e) {
    return input
  }
}

export function tryParseOrNull(input) {
  try {
    return JSON.parse(input)
  } catch (e) {
    return null
  }
}

//Flattenizer is nice, but unfortunately its array notation is different than Lodash's,
// so we use regex to make it consistent with Lodash'a array notation here
export function flattenObj(obj) {
  const flattenObj = Flattenizer.flatten(obj)
  const flattenizerArrayNotation = new RegExp(/\.([0-9][0-9]*)\./)
  return _.mapKeys(flattenObj, (value, key) => {
    const matchResult = key.match(flattenizerArrayNotation)
    if (matchResult) {
      const arrayIndex = matchResult[1]
      return key.replace(flattenizerArrayNotation, `[${arrayIndex}].`)
    } else {
      return key
    }
  })
}

export function objectDiff(object, base) {
  const changes = (object, base) => {
    return _.transform(object, function (result, value, key) {
      if (base && !_.isEqual(value, base[key])) {
        result[key] = (_.isObject(value) && _.isObject(base[key])) ? changes(value, base[key]) : value
      }
    })
  }
  return changes(object, base)
}

export function removeEmptyProperties(obj) {
  if (_.isEmpty(obj)) {
    return obj
  } else {
    const objCopy = _.cloneDeep(obj)
    Object.keys(objCopy).forEach(key => {
      if (!_.isEmpty(objCopy[key]) && _.isObject(objCopy[key])) {
        objCopy[key] = removeEmptyProperties(objCopy[key])
      } else if (_.isEmpty(objCopy[key])) {
        delete objCopy[key]
      }
    })
    return objCopy
  }
}
