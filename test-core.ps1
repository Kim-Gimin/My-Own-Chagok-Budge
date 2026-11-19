param([Parameter(Mandatory=$true)][string]$Jdk, [string]$ExampleFile, [string]$TemplateFile)
$ErrorActionPreference='Stop'
$taskTestOutput=Join-Path $env:TEMP ('chagok-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskTestOutput -Force | Out-Null
$taskCoreFiles=@("$PSScriptRoot\app\src\main\java\kr\chagok\ledger\Ledger.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\HistoryData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\AmountKeypad.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\StatisticsData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\StatisticsPeriod.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\OverviewData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\PinCrypto.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\XlsxIO.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\IconGeometry.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\TransactionType.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\HistoryActionsState.java", "$PSScriptRoot\tests\CoreTests.java")
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $taskTestOutput $taskCoreFiles
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
if($TemplateFile){
    if(!$ExampleFile){$ExampleFile=Join-Path $PSScriptRoot 'tests/android/assets/example.xlsx'}
    & "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests $ExampleFile $TemplateFile
}
elseif($ExampleFile){& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests $ExampleFile}
else {& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests}
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
