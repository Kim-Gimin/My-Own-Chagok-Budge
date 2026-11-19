param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [Parameter(Mandatory=$true)][string]$BuildTools,
    [Parameter(Mandatory=$true)][string]$Adb,
    [Parameter(Mandatory=$true)][string]$Serial,
    [ValidateSet('full','upgrade','preview','revision','history','overlay','workflow','iconbaseline','icon','settings','investment','comparison','consistency','planning','recovery','recoverybaseline','recoveryupgrade','opening','assetguard','assetbaseline','assetupgrade','guide','guidebaseline','guideupgrade','reset','resetverify','back','backbefore','startup','startupbefore','living','livingdark')][string]$Mode='full'
)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-\d+$'){throw 'Use a disposable Android emulator. These tests reset this app database.'}
$taskOut=Join-Path $env:TEMP ('chagok-android-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$taskOut\tests\classes","$taskOut\tests\dex" -Force | Out-Null
function Run-Checked([string]$Tool,[string[]]$Arguments){ & $Tool @Arguments; if($LASTEXITCODE -ne 0){throw "$Tool failed ($LASTEXITCODE)"} }
& "$PSScriptRoot\build-local.ps1" -Jdk $Jdk -AndroidJar $AndroidJar -BuildTools $BuildTools -Output "$taskOut\app" -Keystore "$PSScriptRoot\local-test.keystore"
Run-Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-cp',"$AndroidJar;$taskOut\app\classes.jar",'-d',"$taskOut\tests\classes","$PSScriptRoot\tests\android\DeviceChecks.java","$PSScriptRoot\tests\android\AssetGuardChecks.java","$PSScriptRoot\tests\android\UserGuideChecks.java","$PSScriptRoot\tests\android\ResetChecks.java","$PSScriptRoot\tests\android\BackNavigationChecks.java","$PSScriptRoot\tests\android\StartupChecks.java","$PSScriptRoot\tests\android\LivingBudgetChecks.java")
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
if($Mode -eq 'reset'){
    $taskResetOutput=$taskResult -join "`n"
    if($taskResetOutput -notmatch 'RESET_READY: (\d+) checks; BACKUP_PATH: ([^;]+); BACKUP_SHA256: ([a-f0-9]+)'){throw 'Reset did not reach its confirmed delete step'}
    $taskResetCount=$Matches[1];$taskBackupPath=$Matches[2];$taskBackupHash=$Matches[3]
    $taskVerify=& $Adb -s $Serial shell am instrument -w -e mode resetverify kr.chagok.ledger.tests/kr.chagok.ledger.DeviceChecks
    $taskVerify
    if(($taskVerify -join "`n") -notmatch 'PASS: \d+ Android integration checks'){throw 'Post-reset verification failed'}
    Run-Checked $Adb @('-s',$Serial,'pull',('/sdcard/'+$taskBackupPath),"$taskOut\preserved-backup.xlsx")
    if((Get-FileHash -LiteralPath "$taskOut\preserved-backup.xlsx" -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskBackupHash){throw 'Public backup changed or disappeared after reset'}
    Run-Checked $Adb @('-s',$Serial,'pull','/sdcard/Download/ChagokTest',"$taskOut\public-evidence")
    Write-Output "PASS: $taskResetCount pre-reset checks; OS process termination expected; post-reset checks passed; exported backup SHA256 unchanged"
    Write-Output "Reset evidence: $taskOut"
}elseif(($taskResult -join "`n") -notmatch 'PASS: \d+ Android integration checks'){throw 'Android integration tests failed'}
