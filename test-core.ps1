param([Parameter(Mandatory=$true)][string]$Jdk, [string]$ExampleFile)
$ErrorActionPreference='Stop'
$taskTestOutput=Join-Path $env:TEMP ('chagok-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskTestOutput -Force | Out-Null
$taskCoreFiles=@("$PSScriptRoot\app\src\main\java\kr\chagok\ledger\Ledger.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\StatisticsData.java", "$PSScriptRoot\app\src\main\java\kr\chagok\ledger\XlsxIO.java", "$PSScriptRoot\tests\CoreTests.java")
& "$Jdk\bin\javac.exe" -encoding UTF-8 -d $taskTestOutput $taskCoreFiles
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
if($ExampleFile){& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests $ExampleFile}
else {& "$Jdk\bin\java.exe" -cp $taskTestOutput kr.chagok.ledger.CoreTests}
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
