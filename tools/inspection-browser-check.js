(async()=>{
 const checks=[],sleep=ms=>new Promise(r=>setTimeout(r,ms));
 const check=(ok,label)=>{if(!ok)throw new Error(label);checks.push('PASS '+label);};
 const until=async(fn,label)=>{for(let i=0;i<250;i++){if(fn())return;await sleep(100);}throw new Error(label+' '+document.querySelector('#dialog-error')?.textContent);};
 const click=s=>{const e=document.querySelector(s);if(!e)throw new Error('Missing '+s);e.click();};
 const fill=(name,v)=>{const e=document.querySelector('#modal [name="'+name+'"]');e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));};
 const api=async(p,b)=>{const s=await fetch('/api/session').then(r=>r.json()),r=await fetch('/api'+p,{method:b===undefined?'GET':'POST',headers:b===undefined?{}:{'Content-Type':'application/json','X-CSRF-Token':s.csrf},body:b===undefined?undefined:JSON.stringify(b)}),d=await r.json();if(!r.ok)throw new Error(d.message);return d;};
 const nav=p=>{history.pushState({},'',p);dispatchEvent(new PopStateEvent('popstate'));};
 const submit=async(kind,button)=>{if(button)click(button);else document.querySelector('[data-form="'+kind+'"]').requestSubmit();await until(()=>!document.querySelector('#modal').open,'save '+kind);};
 const login=async(admin)=>{click('[data-action="login"]');fill('email',admin?'admin@campusrent.example':'demo@campusrent.example');fill('password',admin?'CampusAdmin123!':'Campus123!');await submit('auth');};
 const logout=async()=>{click('[data-action="logout"]');await until(()=>document.querySelector('[data-action="login"]'),'logout');};
 const transition=async(id,target)=>{click(`[data-action="transition"][data-id="${id}"][data-target="${target}"]`);await submit('transition');};
 await until(()=>document.querySelector('[data-action="login"]'),'startup');await login(true);
 const components=[{name:'본체',quantity:1,body:true,required:true},{name:'배터리',quantity:2,body:false,required:true}],stamp=Date.now();
 const draft={code:'INS-UI-'+stamp,name:'검수 화면 검증 '+stamp,modelId:2,location:'검수 시험실',department:'검증',allowed:true,status:'NORMAL',components};
 const created=await api('/admin/equipment',draft),asset=created.id;check(!!asset,'isolated inspection fixture created');await logout();await login(false);nav('/equipment');
 const d=new Date(Date.now()+11*86400000),day=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);
 for(const [name,v] of [['start',day+'T10:00'],['end',day+'T12:00']]){const e=document.querySelector('[data-form="conditions"] [name="'+name+'"]');e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));}
 document.querySelector('[data-form="conditions"]').requestSubmit();click(`[data-action="add"][data-id="E${asset}"]`);click('[data-action="checkout"]');document.querySelector('#modal [name="agree"]').checked=true;await submit('checkout');
 const item=(await api('/state')).records.find(r=>r.resourceId==='E'+asset);check(!!item,'request submitted');await logout();await login(true);nav('/admin');await transition(item.id,'승인');
 click(`[data-action="backfill"][data-id="${item.id}"]`);const actual=new Date(Date.now()-5*60000);fill('actualAt',new Date(actual.getTime()-actual.getTimezoneOffset()*60000).toISOString().slice(0,16));fill('reason','현장 전달 후 화면 검증 등록');document.querySelector('#modal [name="confirmed"]').checked=true;await submit('transition');
 let h=await api('/requests/'+item.id+'/inspection-history');check(h.checkout.SOURCE_CODE==='BACKFILL'&&!!h.checkout.ACTUAL_CHECKOUT_AT,'backfill source and actual timestamp persisted');check(h.checkoutLog.some(l=>l.REASON_TEXT==='현장 전달 후 화면 검증 등록'),'backfill reason retained');
 await logout();await login(false);nav('/records');await transition(item.id,'반납 요청');await logout();await login(true);nav('/admin');
 click(`[data-action="inspection"][data-id="${item.id}"]`);fill('quantity-1','1');fill('condition-0','DAMAGED');fill('note-0','본체 외관 파손 확인');fill('reason','배터리 1개 추가 반환 대기');await submit('inspection','[data-form="inspection"] [value="반납 보류"]');
 h=await api('/requests/'+item.id+'/inspection-history');check(h.rounds.length===1&&h.rounds[0].components[0].NOTE_TEXT==='본체 외관 파손 확인','damage and component note persisted');check((await api('/catalog')).find(r=>r.id==='E'+asset).status==='점검','damage automatically restricts equipment');
 click(`[data-action="inspection-history"][data-id="${item.id}"]`);await until(()=>document.querySelector('#modal').open,'history dialog');check(document.querySelector('#modal').textContent.includes('사후 출고 등록')&&document.querySelector('#modal').textContent.includes('본체 외관 파손 확인'),'history dialog shows checkout source and damage');document.querySelector('#modal').close();
 click(`[data-action="inspection"][data-id="${item.id}"]`);fill('note-1','추가 배터리 반환 확인');await submit('inspection','[data-form="inspection"] [value="반납 완료"]');
 h=await api('/requests/'+item.id+'/inspection-history');check(h.rounds.length===2&&h.rounds[1].components[1].CUMULATIVE_QTY===2,'additional return round accumulates quantities');check(h.rounds[0].components[0].NOTE_TEXT==='본체 외관 파손 확인','earlier damage remains unchanged');
 click(`[data-action="finish-inspection"][data-id="E${asset}"]`);await until(()=>!document.querySelector(`[data-action="finish-inspection"][data-id="E${asset}"]`),'inspection completion');check((await api('/catalog')).find(r=>r.id==='E'+asset).status==='정상','inspection completion restores equipment');
 await logout();await login(false);nav('/history');click(`[data-action="inspection-history"][data-id="${item.id}"]`);await until(()=>document.querySelector('#modal').open,'owner detail');check(document.querySelector('#modal').textContent.includes('2회차'),'student can read own round details');document.querySelector('#modal').close();await logout();await login(true);
 const current=(await api('/admin/manage')).equipment.find(e=>e.EQUIPMENT_ID===asset);await api('/admin/equipment/'+asset,{...draft,version:current.ROW_VERSION,status:'STOPPED',allowed:false,reason:'검수 화면 시험 종료'});check((await api('/catalog')).find(r=>r.id==='E'+asset).status==='중지','fixture stopped without deleting history');await logout();nav('/');
 return {status:'pass',text:'PASS '+checks.length+' inspection browser checks\n'+checks.join('\n')};
})()
