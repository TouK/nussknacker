#!/bin/bash -ex

# setup other containers using auto-executed scripts
while IFS= read -r script; do
  "$script"
done < <(find /app/scripts/auto-executed -type f -name '*.sh' | sort)

echo "Setup done!"
# loop forever (you can use manually called utils scripts now)
tail -f /dev/null
