(async()=>{
  const checks=[],sleep=ms=>new Promise(r=>setTimeout(r,ms));
  const check=(ok,label)=>{if(!ok)throw new Error(label);checks.push('PASS '+label);};
  const until=async(fn,label)=>{for(let i=0;i<180;i++){if(fn())return;await sleep(100);}throw new Error('Timed out: '+label);};
  const click=selector=>{const el=document.querySelector(selector);if(!el)throw new Error('Missing '+selector);el.click();};
  const fill=(name,value)=>{const el=document.querySelector('#modal [name="'+name+'"]');el.value=value;el.dispatchEvent(new Event('input',{bubbles:true}));};
  const api=async path=>{const r=await fetch('/api'+path);if(!r.ok)throw new Error('API '+path);return r.json();};
  const submit=async type=>{document.querySelector('[data-form="'+type+'"]').requestSubmit();await until(()=>!document.querySelector('#modal').open,'saved '+type);await sleep(200);};
  const nav=path=>{history.pushState({},'',path);dispatchEvent(new PopStateEvent('popstate'));};
  await until(()=>document.querySelector('[data-action="login"]'),'startup');click('[data-action="login"]');fill('email','admin@campusrent.example');fill('password','CampusAdmin123!');await submit('auth');
  await until(()=>document.querySelector('[data-action="logout"]'),'admin session ready');
  nav('/admin/equipment');check(document.querySelector('h1').textContent==='장비 관리','equipment management route');
  const stamp=String(Date.now()),ids=[];
  for(let i=0;i<2;i++){
    click('[data-manage="equipment"][data-id=""]');fill('code','UI-'+stamp+'-'+i);fill('name','관리 화면 검증 장비 '+i);fill('location','관리 화면 검증 보관실');fill('modelId','2');await submit('manage-equipment');
    const snapshot=await api('/admin/manage');const eq=snapshot.equipment.find(e=>e.ASSET_CODE==='UI-'+stamp+'-'+i);check(!!eq,'equipment registration '+i);ids.push(eq.EQUIPMENT_ID);
  }
  nav('/admin/alternatives');click('[data-manage="alternative"][data-id=""]');fill('sourceId',String(ids[0]));fill('targetId',String(ids[1]));fill('reason','관리 화면 검증용 후보');fill('difference','동일 모델 고정 세트');await submit('manage-alternative');
  let catalog=await api('/catalog');check(catalog.find(r=>r.id==='E'+ids[0]).alternatives.includes('E'+ids[1]),'directed alternative reflected in catalog');
  const snapshot=await api('/admin/manage'),index=snapshot.alternatives.findIndex(a=>a.SOURCE_EQUIPMENT_ID===ids[0]&&a.TARGET_EQUIPMENT_ID===ids[1]);
  click('[data-manage="alternative"][data-id="'+index+'"]');document.querySelector('#modal [name="enabled"]').checked=false;await submit('manage-alternative');
  catalog=await api('/catalog');check(!catalog.find(r=>r.id==='E'+ids[0]).alternatives.includes('E'+ids[1]),'alternative deactivation');
  nav('/admin/facilities');click('[data-manage="facility"][data-id=""]');fill('code','UI-F-'+stamp);fill('name','관리 화면 검증 시설');fill('location','관리 화면 검증 장소');document.querySelector('#modal [name="purpose-3"]').checked=true;await submit('manage-facility');
  const facility=(await api('/admin/manage')).facilities.find(f=>f.FACILITY_CODE==='UI-F-'+stamp);check(!!facility,'facility registration');
  click('[data-manage="block"][data-id="'+facility.FACILITY_ID+'"]');const d=new Date(Date.now()+50*86400000),day=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);fill('start',day+'T10:00');fill('end',day+'T12:00');fill('reason','관리 화면 검증 일정');await submit('manage-block');
  const block=(await api('/admin/manage')).blocks.find(b=>b.FACILITY_ID===facility.FACILITY_ID);check(!!block,'facility block registration');
  click('[data-manage="edit-block"][data-id="'+block.BLOCK_ID+'"]');fill('end',day+'T13:00');fill('reason','관리 화면 검증 일정 수정');await submit('manage-block');
  check((await api('/admin/manage')).blocks.find(b=>b.BLOCK_ID===block.BLOCK_ID).REASON_TEXT==='관리 화면 검증 일정 수정','facility block edit');
  click('[data-manage="cancel-block"][data-id="'+block.BLOCK_ID+'"]');fill('reason','관리 화면 검증 종료');await submit('manage-cancel-block');
  check((await api('/admin/manage')).blocks.find(b=>b.BLOCK_ID===block.BLOCK_ID).ENABLED_YN==='N','block cancellation retains row');
  check(!(await api('/state')).reservations.some(r=>r.id==='block-'+block.BLOCK_ID),'cancelled block releases availability');
  click('[data-manage="block-history"][data-id="'+facility.FACILITY_ID+'"]');await until(()=>document.querySelector('#modal').open,'block audit dialog');
  check(document.querySelector('#modal').textContent.includes('관리 화면 검증 종료'),'block history UI');document.querySelector('#modal').close();
  click('[data-manage="facility"][data-id="'+facility.FACILITY_ID+'"]');fill('capacity','6');document.querySelectorAll('#modal .hours-edit').forEach(row=>{row.querySelector('[name="day-enabled"]').checked=row.dataset.day==='1';});fill('status','STOPPED');document.querySelector('#modal [name="allowed"]').checked=false;await submit('manage-facility');
  const final=await api('/admin/manage');check(final.hours.filter(h=>h.FACILITY_ID===facility.FACILITY_ID).length===1,'weekly hours edit');check(final.facilities.find(f=>f.FACILITY_ID===facility.FACILITY_ID).CAPACITY===6,'capacity edit');
  nav('/admin/equipment');for(const id of ids){click('[data-manage="equipment"][data-id="'+id+'"]');fill('status','STOPPED');fill('reason','관리 화면 검증 종료');document.querySelector('#modal [name="allowed"]').checked=false;await submit('manage-equipment');}
  check((await api('/admin/manage')).equipment.filter(e=>ids.includes(e.EQUIPMENT_ID)).every(e=>e.OPERATING_STATUS==='STOPPED'&&e.LEND_ALLOWED_YN==='N'),'test equipment safely stopped without deleting history');
  nav('/admin/models');check(document.querySelector('h1').textContent==='품목·모델·사양 관리','model route');
  click('[data-manage="type"][data-id=""]');fill('name','화면 검증 '+stamp);fill('category','검증');await submit('manage-type');
  let md=await api('/admin/models');const type=md.types.find(t=>t.TYPE_NAME==='화면 검증 '+stamp);check(!!type,'type registration');
  click('[data-manage="spec"][data-id=""]');fill('typeId',String(type.TYPE_ID));fill('name','해상도');fill('unit','px');await submit('manage-spec');
  md=await api('/admin/models');const spec=md.items.find(i=>i.TYPE_ID===type.TYPE_ID);check(!!spec,'spec item registration');
  click('[data-manage="model"][data-id=""]');const typeSelect=document.querySelector('#modal [name="typeId"]');typeSelect.value=String(type.TYPE_ID);typeSelect.dispatchEvent(new Event('change',{bubbles:true}));fill('code','UI-M-'+stamp);fill('name','화면 검증 모델');fill('value','1920');fill('verify','CONFIRMED');await submit('manage-model');
  md=await api('/admin/models');const model=md.models.find(m=>m.MODEL_CODE==='UI-M-'+stamp);check(md.values.some(v=>v.MODEL_ID===model.MODEL_ID&&v.SPEC_VALUE==='1920'),'model spec value persisted');
  click('[data-manage="model"][data-id="'+model.MODEL_ID+'"]');fill('name','화면 검증 모델 수정');await submit('manage-model');check((await api('/admin/models')).models.find(m=>m.MODEL_ID===model.MODEL_ID).MODEL_NAME==='화면 검증 모델 수정','model update with revision');
  click('[data-manage="type"][data-id="'+type.TYPE_ID+'"]');document.querySelector('#modal [name="enabled"]').checked=false;await submit('manage-type');check((await api('/admin/models')).types.find(t=>t.TYPE_ID===type.TYPE_ID).ENABLED_YN==='N','test type safely disabled');
  nav('/');return {status:'pass',text:'PASS '+checks.length+' admin browser checks\n'+checks.join('\n')};
})()
