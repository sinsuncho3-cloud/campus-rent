$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$seed=Get-Content -LiteralPath (Join-Path $root 'db/oracle11g/recommendation-seed.json') -Raw -Encoding UTF8 | ConvertFrom-Json
function Quote-Sql([string]$value){return "'"+$value.Replace("'","''")+"'"}
$lines=New-Object 'System.Collections.Generic.List[string]'
$lines.Add('-- Oracle 11g additive recommendation data. Existing names/IDs/keywords are preserved.')
$lines.Add('-- Generated from recommendation-seed.json. Run as one transaction; roll back on any error.')
$lines.Add('-- No DDL or deletes. New facility purposes apply ONLY to named original DEMO facilities, never actual facilities.')
foreach($p in $seed){
 $code=Quote-Sql $p.code;$name=Quote-Sql $p.name;$description=Quote-Sql $p.description
 $lines.Add("INSERT INTO USE_PURPOSE(PURPOSE_ID,PURPOSE_CODE,PURPOSE_NAME,DESCRIPTION_TEXT) SELECT SQ_USE_PURPOSE.NEXTVAL,$code,$name,$description FROM DUAL WHERE NOT EXISTS(SELECT 1 FROM USE_PURPOSE WHERE PURPOSE_CODE=$code);")
 foreach($effect in @('include','exclude')){foreach($word in $p.$effect){$keyword=Quote-Sql $word;$action=Quote-Sql $effect.ToUpperInvariant();$lines.Add("INSERT INTO PURPOSE_KEYWORD(KEYWORD_ID,PURPOSE_ID,KEYWORD_TEXT,MATCH_MODE,EFFECT_CODE,PRIORITY_NO) SELECT SQ_PURPOSE_KEYWORD.NEXTVAL,P.PURPOSE_ID,$keyword,'CONTAINS',$action,50 FROM USE_PURPOSE P WHERE P.PURPOSE_CODE=$code AND NOT EXISTS(SELECT 1 FROM PURPOSE_KEYWORD K WHERE K.PURPOSE_ID=P.PURPOSE_ID AND K.KEYWORD_TEXT=$keyword AND K.MATCH_MODE='CONTAINS' AND K.EFFECT_CODE=$action);")}}
 foreach($rule in $p.equipment){$type=Quote-Sql $rule[0];$need=Quote-Sql $rule[1];$reason=Quote-Sql $rule[2];$lines.Add("INSERT INTO PURPOSE_EQUIPMENT_RULE(PURPOSE_ID,TYPE_ID,NEED_LEVEL,REASON_TEXT,DISPLAY_ORDER) SELECT P.PURPOSE_ID,T.TYPE_ID,$need,$reason,1 FROM USE_PURPOSE P CROSS JOIN EQUIPMENT_TYPE T WHERE P.PURPOSE_CODE=$code AND T.TYPE_NAME=$type AND NOT EXISTS(SELECT 1 FROM PURPOSE_EQUIPMENT_RULE R WHERE R.PURPOSE_ID=P.PURPOSE_ID AND R.TYPE_ID=T.TYPE_ID);")}
 $facilities=@();if($p.code -eq 'CONTENT_VIDEO'){$facilities=@('DEMO-STUDIO01','DEMO-STUDIO02')};if($p.code -in @('JOB_INTERVIEW','STUDY')){$facilities=@('DEMO-ROOM01','DEMO-ROOM02','DEMO-STUDY01')}
 foreach($facility in $facilities){$fc=Quote-Sql $facility;$lines.Add("UPDATE FACILITY F SET ROW_VERSION=ROW_VERSION+1 WHERE F.IS_DEMO_YN='Y' AND F.FACILITY_CODE=$fc AND NOT EXISTS(SELECT 1 FROM FACILITY_PURPOSE X JOIN USE_PURPOSE P ON P.PURPOSE_ID=X.PURPOSE_ID WHERE X.FACILITY_ID=F.FACILITY_ID AND P.PURPOSE_CODE=$code);");$lines.Add("INSERT INTO FACILITY_PURPOSE(FACILITY_ID,PURPOSE_ID) SELECT F.FACILITY_ID,P.PURPOSE_ID FROM FACILITY F CROSS JOIN USE_PURPOSE P WHERE F.IS_DEMO_YN='Y' AND F.FACILITY_CODE=$fc AND P.PURPOSE_CODE=$code AND NOT EXISTS(SELECT 1 FROM FACILITY_PURPOSE X WHERE X.FACILITY_ID=F.FACILITY_ID AND X.PURPOSE_ID=P.PURPOSE_ID);")}
}
[IO.File]::WriteAllText((Join-Path $root 'db/oracle11g/12_recommendations.sql'),[string]::Join([Environment]::NewLine,$lines)+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))
Write-Output ('Generated statements: '+($lines.Count-3))
