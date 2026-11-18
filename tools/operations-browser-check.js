(async()=>{
 const checks=[],sleep=ms=>new Promise(r=>setTimeout(r,ms)),check=(v,s)=>{if(!v)throw new Error(s);checks.push('PASS '+s);};
 const until=async(f,s)=>{for(let i=0;i<250;i++){if(f())return;await sleep(100);}throw new Error(s+' '+document.querySelector('#dialog-error')?.textContent);};
 const click=s=>{const e=document.querySelector(s);if(!e)throw new Error('Missing '+s);e.click();};
 const fill=(n,v)=>{const e=document.querySelector('#modal [name="'+n+'"]');e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));};
 const submit=async(kind)=>{document.querySelector('[data-form="'+kind+'"]').requestSubmit();await until(()=>!document.querySelector('#modal').open,'save '+kind);};
 const api=async(p,b)=>{const s=await fetch('/api/session').then(r=>r.json());return fetch('/api'+p,{method:b===undefined?'GET':'POST',headers:b===undefined?{}:{'Content-Type':'application/json','X-CSRF-Token':s.csrf},body:b===undefined?undefined:JSON.stringify(b)});};
 const nav=p=>{history.pushState({},'',p);dispatchEvent(new PopStateEvent('popstate'));};
 await until(()=>document.querySelector('[data-action="signup"]'),'startup');check((await fetch('/api/health/live')).ok&&(await fetch('/api/health/ready')).ok,'liveness and Oracle readiness');
 const email='ops-'+crypto.randomUUID()+'@campusrent.example',first='Initial-'+crypto.randomUUID(),next='Changed-'+crypto.randomUUID();
 click('[data-action="signup"]');fill('name','운영 보완 시험');fill('email',email);fill('password',first);await submit('auth');
 nav('/equipment');const d=new Date(Date.now()+19*86400000),day=new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10);
 for(const [n,v] of [['start',day+'T10:00'],['end',day+'T12:00']]){const e=document.querySelector('[data-form="conditions"] [name="'+n+'"]');e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));}document.querySelector('[data-form="conditions"]').requestSubmit();click('[data-action="add"][data-id="E2"]');click('[data-action="checkout"]');document.querySelector('#modal [name="agree"]').checked=true;
 const original=window.fetch,keys=[];let firstIds,drop=true;
 window.fetch=async function(url,options){const response=await original.apply(this,arguments);if(String(url)==='/api/requests'){keys.push(JSON.parse(options.body).requestKey);if(drop&&response.ok){firstIds=await response.clone().json();drop=false;throw new TypeError('simulated lost response');}}return response;};
 try{document.querySelector('[data-form="checkout"]').requestSubmit();await until(()=>!document.querySelector('#dialog-error').hidden,'lost response shown');check(document.querySelector('#modal').open,'failed response preserves confirmation and selection');await sleep(100);await submit('checkout');}finally{window.fetch=original;}
 check(keys.length===2&&keys[0]===keys[1],'retry preserves request key');let state=await (await api('/state')).json();check(state.records.length===1&&state.records[0].id===firstIds[0],'lost response retry stores one request');
 const record=state.records[0];await api('/requests/'+record.id+'/transition',{target:'취소',version:record.version,details:{}});
 const historyPage=await (await api('/state?page=0&size=1&view=history')).json();check(historyPage.records.length===1&&historyPage.pageInfo.size===1,'paged history returns bounded owner records');
 click('[data-action="password"]');fill('currentPassword',first);fill('newPassword',next);fill('confirmPassword',next);await submit('password');check(!(await (await api('/session')).json()).user,'password change revokes current session');
 const denied=await api('/login',{email,password:first});check(denied.status===400,'old password denied');click('[data-action="login"]');fill('email',email);fill('password',next);await submit('auth');check(!!(await (await api('/session')).json()).user,'new password accepted');nav('/history');await until(()=>document.querySelector('main').textContent.includes('전체 1건'),'history page loaded');check(document.querySelector('main').textContent.includes('전체 1건'),'history pagination UI');
 click('[data-action="logout"]');await until(()=>document.querySelector('[data-action="login"]'),'logout');nav('/');
 return {status:'pass',text:'PASS '+checks.length+' operations browser checks\n'+checks.join('\n')};
})()
