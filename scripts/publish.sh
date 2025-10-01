#!/usr/bin/env sh

set -e

./gradlew clean build -Ptargets=all

./gradlew :dokka:dokkaGenerate
rm -rf ./docs/*
cp -R ./dokka/build/dokka/html/* ./docs/

version=$(./gradlew properties -q | awk '/^version:/ {print $2}')
git add --all
git commit -m "Added documentation for '$version'."
git push

git tag "$version" -f
git push --tags -f

./gradlew publishAllPublicationsToMavenCentralRepository -Ptargets=all
git checkout .
