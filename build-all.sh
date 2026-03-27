#!/bin/bash

cd web/oscar-viewer || exit

npm install
npm run build

cd ../.. || exit

./gradlew clean build -x test -x osgi