$ONNX_VERSION = "1.20.0"
$AAR_URL = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${ONNX_VERSION}/onnxruntime-android-${ONNX_VERSION}.aar"
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Definition
$CPP_DIR = Join-Path $SCRIPT_DIR "src\main\cpp\onnxruntime"
$JNILIBS_DIR = Join-Path $SCRIPT_DIR "src\main\jniLibs"

Write-Host "Downloading ONNX Runtime Android ${ONNX_VERSION}..."

$dirs = @(
    "${CPP_DIR}\include",
    "${CPP_DIR}\lib\arm64-v8a",
    "${CPP_DIR}\lib\armeabi-v7a",
    "${CPP_DIR}\lib\x86",
    "${CPP_DIR}\lib\x86_64",
    "${JNILIBS_DIR}\arm64-v8a",
    "${JNILIBS_DIR}\armeabi-v7a",
    "${JNILIBS_DIR}\x86",
    "${JNILIBS_DIR}\x86_64"
)

foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$TMP_DIR = Join-Path $env:TEMP "onnxruntime-download"
New-Item -ItemType Directory -Force -Path $TMP_DIR | Out-Null
$AAR_FILE = Join-Path $TMP_DIR "onnxruntime.aar"
$ZIP_FILE = Join-Path $TMP_DIR "onnxruntime.zip"

Invoke-WebRequest -Uri $AAR_URL -OutFile $AAR_FILE
Copy-Item $AAR_FILE $ZIP_FILE

Expand-Archive -Path $ZIP_FILE -DestinationPath $TMP_DIR -Force

Copy-Item "${TMP_DIR}\headers\*" "${CPP_DIR}\include\" -Force

$abis = @("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
foreach ($abi in $abis) {
    Copy-Item "${TMP_DIR}\jni\${abi}\libonnxruntime.so" "${CPP_DIR}\lib\${abi}\" -Force
    Copy-Item "${TMP_DIR}\jni\${abi}\libonnxruntime.so" "${JNILIBS_DIR}\${abi}\" -Force
}

Remove-Item $TMP_DIR -Recurse -Force

Write-Host "ONNX Runtime libraries downloaded successfully!"
Write-Host "Headers: ${CPP_DIR}\include"
Write-Host "Libs: ${CPP_DIR}\lib"
Write-Host "jniLibs: ${JNILIBS_DIR}"