#!/bin/bash

# Recreate config file
rm -rf ./_env.js
touch ./_env.js

echo "const env = {" >> ./_env.js

while read -r line || [[ -n "$line" ]];
do
  if printf '%s\n' "$line" | grep -q -e '='; then
    varname=$(printf '%s\n' "$line" | sed -e 's/=.*//')
    varvalue=$(printf '%s\n' "$line" | sed -e 's/^[^=]*=//')
  fi
  value=$(printf '%s\n' "${!varname}")
  [[ -z $value ]] && value=${varvalue}
  echo "  $varname: \"$value\"," >> ./_env.js
done < .env

echo "}; typeof module === 'object' ? module.exports = env : window['.env'] = Object.assign((window['.env'] || {}), env);" >> ./_env.js
