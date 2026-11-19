param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [string]$Output = "$PSScriptRoot\build-local",
    [string]$Keystore = "$PSScriptRoot\local-test.keystore"
)
$ErrorActionPreference = 'Stop'
$taskOriginalJavaHome = $env:JAVA_HOME
$env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk).Path
function Run-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed ($LASTEXITCODE)" }
}
try {
    New-Item -ItemType Directory -Path "$Output\classes", "$Output\dex" -Force | Out-Null
    $taskBuildOutput = (Resolve-Path -LiteralPath $Output).Path
    $taskToolsPath = (Resolve-Path -LiteralPath $BuildTools).Path
    $taskPlatformJar = (Resolve-Path -LiteralPath $AndroidJar).Path
    $taskSourceFiles = @(Get-ChildItem -LiteralPath "$PSScriptRoot\app\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName)
    Run-Checked "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$taskPlatformJar,'-d',"$taskBuildOutput\classes") + $taskSourceFiles)
    Run-Checked "$taskToolsPath\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$taskBuildOutput\resources.zip")
    [xml]$taskManifest = Get-Content -LiteralPath "$PSScriptRoot\app\src\main\AndroidManifest.xml" -Raw
    $taskManifest.manifest.SetAttribute('package','kr.chagok.ledger')
    $taskManifest.Save("$taskBuildOutput\AndroidManifest.xml")
    Run-Checked "$taskToolsPath\aapt2.exe" @('link','-o',"$taskBuildOutput\unsigned.apk",'--manifest',"$taskBuildOutput\AndroidManifest.xml",'-I',$taskPlatformJar,'--min-sdk-version','26','--target-sdk-version','36','--version-code','1','--version-name','0.1.0',"$taskBuildOutput\resources.zip")
    Run-Checked "$Jdk\bin\jar.exe" @('--create','--file',"$taskBuildOutput\classes.jar",'-C',"$taskBuildOutput\classes",'.')
    Run-Checked "$Jdk\bin\java.exe" @('-cp',"$taskToolsPath\lib\d8.jar",'com.android.tools.r8.D8','--lib',$taskPlatformJar,'--min-api','26','--output',"$taskBuildOutput\dex", "$taskBuildOutput\classes.jar")
    Run-Checked "$Jdk\bin\jar.exe" @('--update','--file',"$taskBuildOutput\unsigned.apk",'-C',"$taskBuildOutput\dex",'classes.dex')
    Run-Checked "$taskToolsPath\zipalign.exe" @('-f','-p','4',"$taskBuildOutput\unsigned.apk", "$taskBuildOutput\aligned.apk")
    if (!(Test-Path -LiteralPath $Keystore)) {
        Run-Checked "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$Keystore,'-storepass','android','-alias','chagok-local','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Chagok Local Test,O=Chagok,C=KR','-storetype','JKS')
    }
    Run-Checked "$Jdk\bin\java.exe" @('-jar',"$taskToolsPath\lib\apksigner.jar",'sign','--ks',$Keystore,'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','chagok-local','--out',"$taskBuildOutput\chagok-0.1.0.apk", "$taskBuildOutput\aligned.apk")
    Run-Checked "$Jdk\bin\java.exe" @('-jar',"$taskToolsPath\lib\apksigner.jar",'verify','--verbose',"$taskBuildOutput\chagok-0.1.0.apk")
    Write-Output "APK: $taskBuildOutput\chagok-0.1.0.apk"
} finally { $env:JAVA_HOME = $taskOriginalJavaHome }
