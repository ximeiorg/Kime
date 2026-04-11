#!/bin/bash

ONNX_VERSION="1.20.0"
AAR_URL="https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${ONNX_VERSION}/onnxruntime-android-${ONNX_VERSION}.aar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CPP_DIR="${SCRIPT_DIR}/src/main/cpp/onnxruntime"
JNILIBS_DIR="${SCRIPT_DIR}/src/main/jniLibs"

echo "Downloading ONNX Runtime Android ${ONNX_VERSION}..."

mkdir -p "${CPP_DIR}/include"
mkdir -p "${CPP_DIR}/lib/arm64-v8a"
mkdir -p "${CPP_DIR}/lib/armeabi-v7a"
mkdir -p "${CPP_DIR}/lib/x86"
mkdir -p "${CPP_DIR}/lib/x86_64"
mkdir -p "${JNILIBS_DIR}/arm64-v8a"
mkdir -p "${JNILIBS_DIR}/armeabi-v7a"
mkdir -p "${JNILIBS_DIR}/x86"
mkdir -p "${JNILIBS_DIR}/x86_64"

TMP_DIR=$(mktemp -d)
AAR_FILE="${TMP_DIR}/onnxruntime.aar"

curl -L -o "${AAR_FILE}" "${AAR_URL}" || wget -O "${AAR_FILE}" "${AAR_URL}"

cd "${TMP_DIR}"
unzip -q onnxruntime.aar

cp headers/* "${CPP_DIR}/include/"

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    cp "jni/${abi}/libonnxruntime.so" "${CPP_DIR}/lib/${abi}/"
    cp "jni/${abi}/libonnxruntime.so" "${JNILIBS_DIR}/${abi}/"
done

rm -rf "${TMP_DIR}"

echo "ONNX Runtime libraries downloaded successfully!"
echo "Headers: ${CPP_DIR}/include"
echo "Libs: ${CPP_DIR}/lib"
echo "jniLibs: ${JNILIBS_DIR}"