$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$dbRoot=Join-Path $projectRoot 'db/oracle11g'
$ddl=Get-Content (Join-Path $dbRoot '01_tables.sql') -Raw -Encoding UTF8
# Transcribed from the physical ERD, page 2. This is independent of the SQL parser below.
$erd=@'
APP_USER:USER_ID,EMAIL,PASSWORD,USER_NAME,ROLE_CODE,CREATED_AT
EQUIPMENT_TYPE:TYPE_ID,TYPE_NAME,CATEGORY_LABEL,ENABLED_YN
EQUIPMENT_MODEL:MODEL_ID,TYPE_ID,MODEL_CODE,MODEL_NAME,MANUFACTURER,IS_DEMO_YN
EQUIPMENT:EQUIPMENT_ID,MODEL_ID,ASSET_CODE,EQUIPMENT_NAME,STORAGE_LOCATION,DEPARTMENT_NAME,LEND_ALLOWED_YN,OPERATING_STATUS,IS_DEMO_YN,ROW_VERSION,CREATED_AT
EQUIPMENT_COMPONENT:COMPONENT_ID,EQUIPMENT_ID,COMPONENT_NAME,REQUIRED_QTY,BODY_YN,REQUIRED_YN,DISPLAY_ORDER
EQUIPMENT_ALTERNATIVE:SOURCE_EQUIPMENT_ID,TARGET_EQUIPMENT_ID,REASON_TEXT,DIFFERENCE_TEXT,ENABLED_YN,CONFIGURED_BY,CREATED_AT
SPEC_ITEM:SPEC_ITEM_ID,TYPE_ID,SPEC_NAME,DEFAULT_UNIT,DISPLAY_ORDER
MODEL_SPEC:MODEL_ID,SPEC_ITEM_ID,SPEC_VALUE,VALUE_UNIT,VERIFY_STATUS
USE_PURPOSE:PURPOSE_ID,PURPOSE_CODE,PURPOSE_NAME,DESCRIPTION_TEXT,ENABLED_YN
PURPOSE_EQUIPMENT_RULE:PURPOSE_ID,TYPE_ID,NEED_LEVEL,REASON_TEXT,DISPLAY_ORDER
PURPOSE_KEYWORD:KEYWORD_ID,PURPOSE_ID,KEYWORD_TEXT,MATCH_MODE,EFFECT_CODE,PRIORITY_NO,ENABLED_YN
FACILITY:FACILITY_ID,FACILITY_CODE,FACILITY_NAME,FACILITY_TYPE,LOCATION_TEXT,CAPACITY,FIXED_EQUIPMENT_INFO,USE_ALLOWED_YN,OPERATING_STATUS,IS_DEMO_YN,ROW_VERSION
FACILITY_HOURS:FACILITY_ID,WEEKDAY_NO,OPEN_TIME,CLOSE_TIME
FACILITY_PURPOSE:FACILITY_ID,PURPOSE_ID
FACILITY_BLOCK:BLOCK_ID,FACILITY_ID,BLOCK_START_AT,BLOCK_END_AT,REASON_TEXT,REGISTERED_BY,CREATED_AT
SELECTION_ITEM:SELECTION_ITEM_ID,USER_ID,ITEM_KIND,EQUIPMENT_ID,FACILITY_ID,USE_START_AT,USE_END_AT,PEOPLE_COUNT,PURPOSE_ID,ORIGIN_CODE,ACTIVITY_TEXT,ADDED_AT
RENT_REQUEST:REQUEST_ID,REQUEST_NO,APPLICANT_ID,REQUESTED_AT
REQUEST_ITEM:REQUEST_ITEM_ID,REQUEST_ID,ITEM_NO,ITEM_KIND,EQUIPMENT_ID,FACILITY_ID,USE_START_AT,USE_END_AT,PEOPLE_COUNT,PURPOSE_ID,ORIGIN_CODE,ACTIVITY_TEXT,STATUS_CODE,ROW_VERSION,UPDATED_AT
REQUEST_STATUS_LOG:STATUS_LOG_ID,REQUEST_ITEM_ID,FROM_STATUS,TO_STATUS,CHANGED_BY,CHANGED_AT,REASON_TEXT
REQUEST_ALTERNATIVE:REQUEST_ITEM_ID,ALTERNATIVE_KIND,ORIGINAL_EQUIPMENT_ID,ORIGINAL_FACILITY_ID,ORIGINAL_START_AT,ORIGINAL_END_AT,SELECTED_AT
FACILITY_USAGE:USAGE_ID,REQUEST_ITEM_ID,ITEM_KIND,STARTED_AT,STARTED_BY,ENDED_AT,ENDED_BY,CONDITION_NOTE
EQUIPMENT_CHECKOUT:CHECKOUT_ID,REQUEST_ITEM_ID,ITEM_KIND,ASSET_CODE_SNAPSHOT,EQUIPMENT_NAME_SNAPSHOT,SOURCE_CODE,ACTUAL_CHECKOUT_AT,REGISTERED_AT,REGISTERED_BY
CHECKOUT_COMPONENT:CHECKOUT_ID,COMPONENT_SEQ,COMPONENT_NAME,REQUIRED_QTY,DELIVERED_QTY,BODY_YN,REQUIRED_YN,OUT_CONDITION,NOTE_TEXT
RETURN_INSPECTION:CHECKOUT_ID,INSPECTION_ROUND,INSPECTED_BY,INSPECTED_AT,OUTCOME_CODE,HOLD_REASON,NOTE_TEXT
RETURN_COMPONENT:CHECKOUT_ID,INSPECTION_ROUND,COMPONENT_SEQ,RETURNED_QTY,RETURN_CONDITION,NOTE_TEXT
EQUIPMENT_CHECK_LOG:CHECK_LOG_ID,EQUIPMENT_ID,RELATED_ITEM_ID,ACTION_CODE,FROM_STATUS,TO_STATUS,REASON_TEXT,CHECKED_BY,CHECKED_AT
'@
$tables=@{}
$columnCount=0
foreach($match in [regex]::Matches($ddl,'(?ms)^CREATE TABLE ([A-Z_]+) \((.*?)^\);')){
  $name=$match.Groups[1].Value
  $columns=@{}
  $ordered=@()
  foreach($column in [regex]::Matches($match.Groups[2].Value,'(?m)^  ([A-Z_]+) ((?:NUMBER|VARCHAR2|CHAR)(?:\([^\r\n)]+\))?|TIMESTAMP)\b?')){
    $columns[$column.Groups[1].Value]=$column.Groups[2].Value
    $ordered+=$column.Groups[1].Value
  }
  $tables[$name]=@{Body=$match.Groups[2].Value;Columns=$columns;Ordered=$ordered;Keys=@();Indexes=@()}
  $columnCount+=$columns.Count
}
if($tables.Count -ne 26 -or $columnCount -ne 185){throw "Expected 26 tables / 185 columns, got $($tables.Count) / $columnCount"}
foreach($line in ($erd -split '\r?\n')){
  $parts=$line.Split(':')
  if(-not $tables.ContainsKey($parts[0]) -or ($tables[$parts[0]].Ordered -join ',') -ne $parts[1]){throw "ERD column mismatch: $($parts[0])"}
}
Write-Output 'PASS: 26 physical ERD tables and all 185 ordered column names.'
foreach($name in $tables.Keys){
  foreach($key in [regex]::Matches($tables[$name].Body,'(?:PRIMARY KEY|UNIQUE) \(([A-Z_, ]+)\)')){
    $tuple=$key.Groups[1].Value.Replace(' ','')
    $tables[$name].Keys+=,$tuple
    $tables[$name].Indexes+=,$tuple
  }
}
$indexes=Get-Content (Join-Path $dbRoot '03_indexes.sql') -Raw -Encoding UTF8
foreach($index in [regex]::Matches($indexes,'CREATE (?:UNIQUE )?INDEX ([A-Z_]+) ON ([A-Z_]+) \(([A-Z_, ]+)\)')){
  $name=$index.Groups[2].Value
  if(-not $tables.ContainsKey($name)){throw "Unknown index table: $name"}
  $tuple=$index.Groups[3].Value.Replace(' ','')
  foreach($column in $tuple.Split(',')){if(-not $tables[$name].Columns.ContainsKey($column)){throw "Unknown index column: $name.$column"}}
  $tables[$name].Indexes+=,$tuple
}
$fkCount=0
foreach($name in $tables.Keys){
  foreach($fk in [regex]::Matches($tables[$name].Body,'FOREIGN KEY \(([A-Z_, ]+)\) REFERENCES ([A-Z_]+) \(([A-Z_, ]+)\)')){
    $local=$fk.Groups[1].Value.Replace(' ','');$parent=$fk.Groups[2].Value;$remote=$fk.Groups[3].Value.Replace(' ','')
    if(-not $tables.ContainsKey($parent) -or $tables[$parent].Keys -notcontains $remote){throw "FK has no parent PK/unique: $name -> $parent ($remote)"}
    $localCols=$local.Split(',');$remoteCols=$remote.Split(',')
    if($localCols.Count -ne $remoteCols.Count){throw "FK column count mismatch: $name"}
    for($i=0;$i -lt $localCols.Count;$i++){
      if(-not $tables[$name].Columns.ContainsKey($localCols[$i]) -or $tables[$name].Columns[$localCols[$i]] -ne $tables[$parent].Columns[$remoteCols[$i]]){throw "FK type mismatch: $name.$($localCols[$i])"}
    }
    $covered=@($tables[$name].Indexes | Where-Object {$_ -eq $local -or $_.StartsWith($local+',')})
    if($covered.Count -eq 0){throw "FK lacks leading-column index: $name ($local)"}
    $fkCount++
  }
}
Write-Output "PASS: $fkCount foreign keys reference compatible PK/unique columns and have supporting indexes."
$sqlFiles=Get-ChildItem $dbRoot -Filter '*.sql'
$allSql=($sqlFiles | ForEach-Object {Get-Content $_.FullName -Raw -Encoding UTF8}) -join "`n"
foreach($identifier in [regex]::Matches($allSql,'(?:CREATE TABLE|CREATE SEQUENCE|CREATE (?:UNIQUE )?INDEX|CONSTRAINT|PROCEDURE) ([A-Z_]+)')){
  if($identifier.Groups[1].Value.Length -gt 30){throw "Oracle 11g identifier too long: $($identifier.Groups[1].Value)"}
}
$sequences=Get-Content (Join-Path $dbRoot '02_sequences.sql') -Raw -Encoding UTF8
if([regex]::Matches($sequences,'CREATE SEQUENCE').Count -ne 17){throw 'Expected 17 sequences.'}
foreach($reference in [regex]::Matches($allSql,'\b(SQ_[A-Z_]+)\.NEXTVAL')){
  if($sequences -notmatch ('CREATE SEQUENCE '+[regex]::Escape($reference.Groups[1].Value)+'\b')){throw "Missing sequence: $($reference.Groups[1].Value)"}
}
foreach($script in [regex]::Matches((Get-Content (Join-Path $dbRoot 'install_dev.sql') -Raw),'@@([^\r\n ]+)')){
  if(-not (Test-Path (Join-Path $dbRoot $script.Groups[1].Value))){throw "Missing install script: $($script.Groups[1].Value)"}
}
Write-Output 'PASS: sequence references, installer files, and Oracle 11g 30-byte ASCII object names.'
$buildRoot=Join-Path $projectRoot '.work/db-tools'
New-Item -ItemType Directory -Force $buildRoot | Out-Null
& javac -encoding UTF-8 -d $buildRoot (Join-Path $projectRoot 'tools/SeedPasswords.java')
if($LASTEXITCODE -ne 0){throw 'Password verification tool compilation failed.'}
& java -cp $buildRoot SeedPasswords verify (Join-Path $dbRoot '05_users.sql')
if($LASTEXITCODE -ne 0){throw 'Seed password verification failed.'}
Write-Output 'Local checks passed. SQL syntax/PLSQL compilation and transactional smoke tests still require Oracle 11g XE.'
