import {request} from './store.js';
import {configureModels,loadModels,modelsView,modelsAction,modelsSubmit} from './model-admin.js';
import {configureKeywordUI,loadKeywords,keywordView,keywordAction,keywordSubmit} from './recommendation-admin.js';
let data=null,ui;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const status=v=>({NORMAL:'정상',CHECKING:'점검',STOPPED:'운영 중지'}[v]||v);
const input=(name,label,value='',type='text',extra='')=>`<label class="field">${label}<input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
const checkbox=(name,label,checked)=>`<label class="checkrow"><input type="checkbox" name="${name}" ${checked?'checked':''}><span>${label}</span></label>`;
const select=(name,label,options,value)=>`<label class="field">${label}<select name="${name}">${options.map(([v,label])=>`<option value="${esc(v)}" ${String(v)===String(value)?'selected':''}>${esc(label)}</option>`).join('')}</select></label>`;
const button=(action,label,id='')=>`<button type="button" class="btn secondary small" data-manage="${action}" data-id="${esc(id)}">${label}</button>`;
const heading=(title,text)=>`<div class="heading"><p class="breadcrumb">관리자 / ${title}</p><h1>${title}</h1><p>${text}</p></div>`;
export function configureManagement(dependencies){ui=dependencies;configureModels(dependencies);configureKeywordUI(dependencies);}
export async function loadManagement(user){data=user?.role==='admin'?await request('/admin/manage'):null;await Promise.all([loadModels(user),loadKeywords(user)]);}
export function adminNavigation(){return `<nav class="tabs" aria-label="관리자 메뉴">${[['/admin','신청 처리'],['/admin/recommendations','추천 키워드'],['/admin/models','품목·모델·사양'],['/admin/equipment','장비 관리'],['/admin/alternatives','대체 장비 관계'],['/admin/facilities','시설 운영 관리']].map(([p,t])=>`<a class="btn ${location.pathname===p?'':'secondary'} small" href="${p}">${t}</a>`).join('')}</nav>`;}
function name(id){return data.equipment.find(e=>e.EQUIPMENT_ID===Number(id))?.EQUIPMENT_NAME||String(id);}
export function managementView(path){
  if(data&&path==='/admin/recommendations')return keywordView();
  if(data&&path==='/admin/models')return modelsView();
  if(!data)return `<div class="container page"><h1>관리자 로그인이 필요합니다.</h1><button type="button" class="btn" data-action="login">로그인</button></div>`;
  if(path==='/admin/equipment')return `<div class="container page">${heading('장비 관리','개별 장비와 기본 구성품을 관리합니다. 출고 당시 기준과 처리 이력은 보존됩니다.')}<div class="actions">${button('equipment','장비 등록')}</div><div class="cards" style="margin-top:20px">${data.equipment.map(e=>`<article class="panel resource"><span class="status">${status(e.OPERATING_STATUS)} · ${e.LEND_ALLOWED_YN==='Y'?'대여 허용':'대여 중지'}</span><h3>${esc(e.EQUIPMENT_NAME)}</h3><p>${esc(e.ASSET_CODE)} · ${esc(e.STORAGE_LOCATION)}</p><p>${data.components.filter(p=>p.EQUIPMENT_ID===e.EQUIPMENT_ID).map(p=>`${esc(p.COMPONENT_NAME)} ${p.REQUIRED_QTY}개`).join(' · ')}</p>${button('equipment','수정·운영 상태',e.EQUIPMENT_ID)}</article>`).join('')}</div></div>`;
  if(path==='/admin/alternatives')return `<div class="container page">${heading('대체 장비 관계','원래 장비 → 대체 장비의 방향별 관계입니다. 지정해도 예약은 확정되지 않으며 이용 가능 여부를 별도로 검사합니다.')}<div class="actions">${button('alternative','관계 등록')}</div>${data.alternatives.map((a,i)=>`<article class="panel record" style="margin-top:16px"><h3>${esc(name(a.SOURCE_EQUIPMENT_ID))} → ${esc(name(a.TARGET_EQUIPMENT_ID))}</h3><p>${esc(a.REASON_TEXT)}</p><p>${esc(a.DIFFERENCE_TEXT)}</p><div class="row"><span class="status">${a.ENABLED_YN==='Y'?'적용 중':'해제됨'}</span>${button('alternative','수정·적용 해제',i)}</div></article>`).join('')}</div>`;
  return `<div class="container page">${heading('시설 운영 관리','시설 정보·허용 목적·요일별 운영 시간과 제한 일정을 관리합니다. 기존 승인·이용 중 예약과 충돌하는 변경은 차단됩니다.')}<div class="actions">${button('facility','시설 등록')}</div>${data.facilities.map(f=>`<article class="panel record" style="margin-top:16px"><div class="row"><h3>${esc(f.FACILITY_NAME)}</h3><span class="status">${status(f.OPERATING_STATUS)} · ${f.CAPACITY}명</span></div><p>${esc(f.FACILITY_CODE)} · ${esc(f.LOCATION_TEXT)}</p><div class="actions">${button('facility','정보·운영 조건 수정',f.FACILITY_ID)}${button('block','제한 일정 추가',f.FACILITY_ID)}${button('block-history','제한 변경 이력',f.FACILITY_ID)}</div>${data.blocks.filter(b=>b.FACILITY_ID===f.FACILITY_ID).map(b=>`<div class="cart-item"><p>${esc(String(b.BLOCK_START_AT).slice(0,16).replace('T',' '))} ~ ${esc(String(b.BLOCK_END_AT).slice(0,16).replace('T',' '))}</p><p>${esc(b.REASON_TEXT)}</p>${b.ENABLED_YN==='Y'?button('edit-block','제한 일정 수정',b.BLOCK_ID)+button('cancel-block','제한 해제',b.BLOCK_ID):'<span class="status">해제됨</span>'}</div>`).join('')}</article>`).join('')}</div>`;
}
function partRow(p={}){return `<div class="panel component-edit" style="margin-top:10px"><div class="filters">${input('part-name','구성품 이름',p.COMPONENT_NAME||'','text','required maxlength="100"')}${input('part-quantity','수량',p.REQUIRED_QTY||1,'number','required min="1" max="99999" step="1"')}</div>${checkbox('part-body','본체',p.BODY_YN==='Y')}${checkbox('part-required','필수 반환',p.REQUIRED_YN!=='N')}${button('remove-part','이 구성품 제거')}</div>`;}
function equipmentDialog(id){
  const e=data.equipment.find(e=>e.EQUIPMENT_ID===Number(id))||{};
  const parts=id?data.components.filter(p=>p.EQUIPMENT_ID===Number(id)):[{COMPONENT_NAME:'본체',REQUIRED_QTY:1,BODY_YN:'Y',REQUIRED_YN:'Y'}];
  ui.dialog(id?'장비 수정·운영 상태':'장비 등록',`<form data-form="manage-equipment" data-id="${id}"><input type="hidden" name="version" value="${e.ROW_VERSION||1}"><div class="filters">${input('code','개별 관리번호',e.ASSET_CODE||'','text','required maxlength="30"')}${input('name','장비 이름',e.EQUIPMENT_NAME||'','text','required maxlength="100"')}${select('modelId','모델',data.models.map(m=>[m.MODEL_ID,`${m.MODEL_CODE} · ${m.TYPE_NAME}`]),e.MODEL_ID||data.models[0]?.MODEL_ID)}${input('location','보관 장소',e.STORAGE_LOCATION||'','text','required maxlength="200"')}${input('department','담당 부서',e.DEPARTMENT_NAME||'','text','maxlength="100"')}${select('status','운영 상태',[['NORMAL','정상'],['CHECKING','점검'],['STOPPED','운영 중지']],e.OPERATING_STATUS||'NORMAL')}</div>${checkbox('allowed','대여 허용',e.LEND_ALLOWED_YN!=='N')}${input('reason','운영 상태 변경 사유', '', 'text','maxlength="1000"')}<h3 style="margin-top:20px">기본 구성품</h3><p class="note">본체를 한 개 지정하세요. 수정 전 출고 기록은 당시 기준을 유지합니다.</p><div id="manage-parts">${parts.map(partRow).join('')}</div><div class="actions">${button('add-part','구성품 추가')}<button class="btn">저장</button></div></form>`);
}
function alternativeDialog(index){
  const a=index===''?null:data.alternatives[Number(index)],options=data.equipment.map(e=>[e.EQUIPMENT_ID,e.EQUIPMENT_NAME]);
  ui.dialog('대체 장비 관계',`<form data-form="manage-alternative" data-index="${index}">${select('sourceId','원래 장비',options,a?.SOURCE_EQUIPMENT_ID)}${select('targetId','대체 장비',options,a?.TARGET_EQUIPMENT_ID)}${input('reason','지정 이유',a?.REASON_TEXT||'','text','required maxlength="500"')}<label class="field">주요 차이<textarea name="difference" maxlength="1000">${esc(a?.DIFFERENCE_TEXT||'')}</textarea></label>${checkbox('enabled','관계 적용 (해제하려면 체크를 끄세요)',a?.ENABLED_YN!=='N')}<p class="note">같은 품목끼리 지정합니다. 반대 방향은 별도로 등록해야 합니다.</p><div class="actions"><button class="btn">저장</button></div></form>`);
  if(a){document.querySelector('[name="sourceId"]').disabled=true;document.querySelector('[name="targetId"]').disabled=true;}
}
function facilityDialog(id){
  const f=data.facilities.find(f=>f.FACILITY_ID===Number(id))||{},hours=data.hours.filter(h=>h.FACILITY_ID===Number(id)),allowed=data.facilityPurposes.filter(p=>p.FACILITY_ID===Number(id)).map(p=>p.PURPOSE_ID);
  ui.dialog(id?'시설 정보·운영 조건 수정':'시설 등록',`<form data-form="manage-facility" data-id="${id}"><input type="hidden" name="version" value="${f.ROW_VERSION||1}"><div class="filters">${input('code','시설 관리번호',f.FACILITY_CODE||'','text','required maxlength="30"')}${input('name','시설 이름',f.FACILITY_NAME||'','text','required maxlength="100"')}${input('type','시설 유형',f.FACILITY_TYPE||'회의실','text','required maxlength="20"')}${input('location','위치',f.LOCATION_TEXT||'','text','required maxlength="200"')}${input('capacity','수용 인원',f.CAPACITY||4,'number','required min="1" max="999" step="1"')}${select('status','운영 상태',[['NORMAL','정상'],['CHECKING','점검'],['STOPPED','운영 중지']],f.OPERATING_STATUS||'NORMAL')}</div><label class="field">비치 설비 안내<textarea name="info" maxlength="1000">${esc(f.FIXED_EQUIPMENT_INFO||'')}</textarea></label>${checkbox('allowed','이용 허용',f.USE_ALLOWED_YN!=='N')}<h3 style="margin-top:20px">허용 목적</h3>${data.purposes.map(p=>checkbox('purpose-'+p.PURPOSE_ID,esc(p.PURPOSE_NAME),allowed.includes(p.PURPOSE_ID))).join('')}<h3 style="margin-top:20px">요일별 운영 시간</h3>${['월','화','수','목','금','토','일'].map((day,i)=>{const h=hours.find(h=>h.WEEKDAY_NO===i+1);return `<div class="hours-edit panel" style="margin-top:8px" data-day="${i+1}">${checkbox('day-enabled',day+'요일 운영',id?!!h:true)}<div class="filters">${input('open','시작',h?.OPEN_TIME||'09:00','time')}${input('close','종료',h?.CLOSE_TIME||'20:00','time')}</div></div>`;}).join('')}<div class="actions"><button class="btn">저장</button></div></form>`);
}
function blockDialog(facilityId,block){
  const f=data.facilities.find(f=>f.FACILITY_ID===Number(facilityId));
  ui.dialog('시설 이용 제한 일정',`<p>${esc(f.FACILITY_NAME)}</p><form data-form="manage-block" data-id="${facilityId}" data-block="${block?.BLOCK_ID||''}"><input type="hidden" name="version" value="${f.ROW_VERSION}">${input('start','제한 시작',String(block?.BLOCK_START_AT||'').slice(0,16),'datetime-local','required')}${input('end','제한 종료',String(block?.BLOCK_END_AT||'').slice(0,16),'datetime-local','required')}${input('reason','제한 사유',block?.REASON_TEXT||'','text','required maxlength="500"')}<p class="note">승인 예약과 겹치는 일정은 저장할 수 없습니다. 제한 일정은 수정·해제로 관리하며 변경 이력을 보존합니다.</p><div class="actions"><button class="btn">저장</button></div></form>`);
}
export function managementAction(el){
  if(keywordAction(el))return;
  if(modelsAction(el))return;
  const action=el.dataset.manage,id=el.dataset.id;
  if(action==='cancel-block'){
    const b=data.blocks.find(b=>b.BLOCK_ID===Number(id)),f=data.facilities.find(f=>f.FACILITY_ID===b.FACILITY_ID);
    ui.dialog('시설 제한 해제',`<form data-form="manage-cancel-block" data-id="${f.FACILITY_ID}" data-block="${b.BLOCK_ID}"><input name="version" type="hidden" value="${f.ROW_VERSION}"><p>${esc(f.FACILITY_NAME)} · ${esc(b.REASON_TEXT)}</p>${input('reason','해제 사유','','text','required maxlength="500"')}<button class="btn">제한 해제</button></form>`);
  }
  if(action==='block-history')request('/admin/facilities/'+id+'/block-history').then(rows=>ui.dialog('제한 일정 변경 이력',rows.map(r=>`<article class="cart-item"><h3>${esc(({IMPORT:'이관 기준',CREATE:'등록',UPDATE:'수정',CANCEL:'해제'})[r.action])}</h3><p>${esc(r.at)} · ${esc(r.by)}</p><p>${esc(r.reason)}</p><details><summary>변경 전·후</summary><p style="overflow-wrap:anywhere">${esc(r.before||'없음')}</p><p style="overflow-wrap:anywhere">${esc(r.after)}</p></details></article>`).join('')||'<p>이력이 없습니다.</p>')).catch(e=>ui.notify(e.message));
  if(action==='equipment')equipmentDialog(id);
  if(action==='alternative')alternativeDialog(id);
  if(action==='facility')facilityDialog(id);
  if(action==='block')blockDialog(id);
  if(action==='edit-block'){const block=data.blocks.find(b=>b.BLOCK_ID===Number(id));blockDialog(block.FACILITY_ID,block);}
  if(action==='add-part')document.querySelector('#manage-parts').insertAdjacentHTML('beforeend',partRow());
  if(action==='remove-part')el.closest('.component-edit').remove();
}
export async function managementSubmit(form){
  if(await keywordSubmit(form))return;
  if(await modelsSubmit(form))return;
  const f=Object.fromEntries(new FormData(form)),kind=form.dataset.form,id=form.dataset.id;
  let payload={...f,version:Number(f.version),allowed:form.querySelector('[name="allowed"]')?.checked||false};
  if(kind==='manage-equipment'){
    payload.modelId=Number(f.modelId);payload.components=[...form.querySelectorAll('.component-edit')].map(row=>({name:row.querySelector('[name="part-name"]').value,quantity:Number(row.querySelector('[name="part-quantity"]').value),body:row.querySelector('[name="part-body"]').checked,required:row.querySelector('[name="part-required"]').checked}));
    await request('/admin/equipment'+(id?'/'+id:''),payload);
  }
  if(kind==='manage-alternative'){
    const a=form.dataset.index===''?null:data.alternatives[Number(form.dataset.index)];
    await request('/admin/alternatives',{sourceId:a?.SOURCE_EQUIPMENT_ID||Number(f.sourceId),targetId:a?.TARGET_EQUIPMENT_ID||Number(f.targetId),reason:f.reason,difference:f.difference,enabled:form.querySelector('[name="enabled"]').checked,existing:!!a,expected:a?{reason:a.REASON_TEXT,difference:a.DIFFERENCE_TEXT||'',enabled:a.ENABLED_YN==='Y'}:{}});
  }
  if(kind==='manage-facility'){
    payload.capacity=Number(f.capacity);payload.purposeIds=data.purposes.filter(p=>form.querySelector(`[name="purpose-${p.PURPOSE_ID}"]`).checked).map(p=>p.PURPOSE_ID);
    payload.hours=[...form.querySelectorAll('.hours-edit')].filter(row=>row.querySelector('[name="day-enabled"]').checked).map(row=>({day:Number(row.dataset.day),open:row.querySelector('[name="open"]').value,close:row.querySelector('[name="close"]').value}));
    await request('/admin/facilities'+(id?'/'+id:''),payload);
  }
  if(kind==='manage-block')await request('/admin/facilities/'+id+'/blocks'+(form.dataset.block?'/'+form.dataset.block:''),payload);
  if(kind==='manage-cancel-block')await request('/admin/facilities/'+id+'/blocks/'+form.dataset.block+'/cancel',payload);
  await ui.refresh();document.querySelector('#modal').close();ui.notify('저장했습니다. 조회 화면과 운영 조건에 반영되었습니다.');
}
