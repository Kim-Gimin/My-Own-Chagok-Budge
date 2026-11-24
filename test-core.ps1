param([Parameter(Mandatory=$true)][string]$Jdk, [string]$ExampleFile, [string]$TemplateFile)
$ErrorActionPreference='Stop'
$taskTestOutput=Join-Path $env:TEMP ('chagok-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskTestOutput -Force | Out-Null
$taskCoreFiles=@("$PSScriptRoot\tests\PlanXlsxTests.java","$PSScriptRoot\tests\CategoryTests.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\CategoryNames.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\AssetConsistency.java","$PSScriptRoot\tests\AssetGuardTests.java","$PSScriptRoot\tests\OpeningTests.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\OpeningAsset.java","$PSScriptRoot\tests\RecoveryTests.java","$PSScriptRoot\tests\MaturityTests.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\SavingsMovement.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\Ledger.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\HistoryData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\AmountKeypad.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\StatisticsData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\StatisticsPeriod.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\OverviewData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\SpendingSpeedData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\PinCrypto.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\XlsxIO.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\IconGeometry.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\TransactionType.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\HistoryActionsState.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\PlanningData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\BackupArchive.java", "$PSScriptRoot\tests\PlanningTests.java", "$PSScriptRoot\tests\CoreTests.java")
$taskCoreFiles+="$PSScriptRoot\tests\LivingBudgetTests.java"
$taskCoreFiles+="$PSScriptRoot\tests\TimeTests.java"
$taskCoreFiles+="$PSScriptRoot\tests\CategoryOrderTests.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\CategoryOrder.java"
$taskCoreFiles+="$PSScriptRoot\tests\SpendingSpeedTests.java"
$taskCoreFiles+="$PSScriptRoot\tests\BottomNavTests.java","$PSScriptRoot\app\src\main\java\kr\chagok\ledger\BottomNav.java"
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $taskTestOutput $taskCoreFiles
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
if($TemplateFile){
    if(!$ExampleFile){$ExampleFile=Join-Path $PSScriptRoot 'tests/android/assets/example.xlsx'}
    & "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests $ExampleFile $TemplateFile
}
elseif($ExampleFile){& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests $ExampleFile}
else {& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests}
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.PlanningTests
if($LASTEXITCODE -ne 0){throw "Planning tests failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.MaturityTests
if($LASTEXITCODE -ne 0){throw "Maturity checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.RecoveryTests
if($LASTEXITCODE -ne 0){throw "Recovery checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.OpeningTests
if($LASTEXITCODE -ne 0){throw "Opening asset checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.AssetGuardTests
if($LASTEXITCODE -ne 0){throw "Asset consistency tests failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.LivingBudgetTests
if($LASTEXITCODE -ne 0){throw "Living budget tests failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CategoryTests
if($LASTEXITCODE -ne 0){throw "Category normalization/calendar checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.PlanXlsxTests
if($LASTEXITCODE -ne 0){throw "Planning XLSX checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.TimeTests
if($LASTEXITCODE -ne 0){throw "Transaction time checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CategoryOrderTests
if($LASTEXITCODE -ne 0){throw "Category order checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.SpendingSpeedTests
if($LASTEXITCODE -ne 0){throw "Budget/total expense chart checks failed"}

& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.BottomNavTests
if($LASTEXITCODE -ne 0){throw "Bottom navigation checks failed"}
