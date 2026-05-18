#!/bin/bash
# Standalone script to manually update design tokens from the nox repository

echo "Pulling latest design tokens from github.com/pebrd/nox..."
if [ -d "../nox_repo" ]; then
    cd ../nox_repo && git pull && cd ../SpotiTidal
else
    git clone https://github.com/pebrd/nox.git ../nox_repo
fi

if [ -f "../nox_repo/dist/kotlin/NoxTokens.kt" ]; then
    echo "Updating NoxTokens.kt in Conduit KMP codebase..."
    mkdir -p composeApp/src/commonMain/kotlin/nox/designsystem
    cp ../nox_repo/dist/kotlin/NoxTokens.kt composeApp/src/commonMain/kotlin/nox/designsystem/NoxTokens.kt
    echo "Successfully updated design tokens!"
else
    echo "Error: NoxTokens.kt not found in nox_repo/dist/kotlin/"
    exit 1
fi
