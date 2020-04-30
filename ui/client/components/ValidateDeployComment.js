import _ from "lodash"

export default (comment, {requireComment, matchExpression}) => {
  let validated  = {isValid: true, toolTip: undefined}

  if (requireComment && _.isEmpty(comment)) {
    validated = {isValid: false, toolTip: "Comment is required."}
  } else if (requireComment && !_.isEmpty(matchExpression)) {
    const match = comment.match(new RegExp(matchExpression, "g"))
    if (!match) {
      validated = {isValid: false, toolTip: "Comment does not match required pattern."}
    }
  }

  return validated
}

