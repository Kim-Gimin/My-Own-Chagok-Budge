param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('full','upgrade','preview','revision','history','overlay','workflow','iconbaseline','icon','settings','investment','comparison','consistency','planning','recovery','recoverybaseline','recoveryupgrade')][string]$Mode='full'
)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-\d+$'){throw 'Use a disposable Android emulator. These tests reset this app database.'}
$taskOut=Join-Path $env:TEMP ('chagok-android-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$taskOut\tests\classes","$taskOut\tests\dex" -Force | Out-Null
function Run-Checked([string]$Tool,[string[]]$Arguments){ & $Tool @Arguments; if($LASTEXITCODE -ne 0){throw "$Tool failed ($LASTEXITCODE)"} }
& "$PSScriptRoot\build-local.ps1" -Jdk $Jdk -AndroidJar $AndroidJar -BuildTools $BuildTools -Output "$taskOut\app" -Keystore "$PSScriptRoot\local-test.keystore"
Run-Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-cp',"$AndroidJar;$taskOut\app\classes.jar",'-d',"$taskOut\tests\classes","$PSScriptRoot\tests\android\DeviceChecks.java")
Run-Checked "$Jdk\bin\jar.exe" @('--create','--file',"$taskOut\tests\classes.jar",'-C',"$taskOut\tests\classes",'.')
Run-Checked "$Jdk\bin\java.exe" @('-cp',"$BuildTools\lib\d8.jar",'com.android.tools.r8.D8','--lib',$AndroidJar,'--min-api','26','--output',"$taskOut\tests\dex","$taskOut\tests\classes.jar")
Run-Checked "$BuildTools\aapt2.exe" @('link','-o',"$taskOut\tests\unsigned.apk",'--manifest',"$PSScriptRoot\tests\android\AndroidManifest.xml",'-I',$AndroidJar,'-A',"$PSScriptRoot\tests\android\assets")
Run-Checked "$Jdk\bin\jar.exe" @('--update','--file',"$taskOut\tests\unsigned.apk",'-C',"$taskOut\tests\dex",'classes.dex')
Run-Checked "$BuildTools\zipalign.exe" @('-f','-p','4',"$taskOut\tests\unsigned.apk","$taskOut\tests\aligned.apk")
Run-Checked "$Jdk\bin\java.exe" @('-jar',"$BuildTools\lib\apksigner.jar",'sign','--ks',"$PSScriptRoot\local-test.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--ks-key-alias','chagok-local','--out',"$taskOut\tests\tests.apk","$taskOut\tests\aligned.apk")
$taskApk=(Get-ChildItem -LiteralPath "$taskOut\app" -Filter 'chagok-*.apk' | Select-Object -First 1).FullName
Run-Checked $Adb @('-s',$Serial,'install','-r',$taskApk)
Run-Checked $Adb @('-s',$Serial,'install','-r',"$taskOut\tests\tests.apk")
$taskInstrumentArgs=@('-s',$Serial,'shell','am','instrument','-w')
if($Mode -ne 'full'){$taskInstrumentArgs+=@('-e','mode',$Mode)}
$taskInstrumentArgs+='kr.chagok.ledger.tests/kr.chagok.ledger.DeviceChecks'
$taskResult=& $Adb @taskInstrumentArgs
$taskResult
if(($taskResult -join "`n") -notmatch 'PASS: \d+ Android integration checks'){throw 'Android integration tests failed'}
