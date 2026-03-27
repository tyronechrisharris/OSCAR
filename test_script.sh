#!/bin/bash
git ls-files | grep -E '\.class$|\.jar$' | while read -r line; do
  if [[ "$line" != *"lib/"* ]] && [[ "$line" != *"libs/"* ]] && [[ "$line" != *"jars/"* ]] && [[ "$line" != *"gradle-wrapper.jar" ]] && [[ "$line" != *"test/resources"* ]] && [[ "$line" != *"dist/"* ]]; then
    echo "$line"
  fi
done
