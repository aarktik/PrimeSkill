'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createBrowser}=require('./ui-browser-client.cjs');
const name=process.argv[2]||'chrome',base=process.argv[3]||'http://127.0.0.1:18088';
const password=process.env.UI_TEST_PASSWORD;
assert.ok(password,'Provide UI_TEST_PASSWORD for disposable fixture accounts');
assert.ok(['127.0.0.1','localhost'].includes(new URL(base).hostname),'Disposable loopback required');
const output=path.resolve(__dirname,'../code/target/role-c-e-followup/'+name+'-'+Date.now());
const report={status:'RUNNING',browser:name,cases:[],layouts:[]};let b;
(async()=>{try{
    let marker;
    for(let i=0;i<80;i++){try{marker=await fetch(base+'/actuator/health');if(marker.ok)break;}catch{}await new Promise(r=>setTimeout(r,250));}
    assert.equal(marker?.headers.get('X-PrimeSkill-UI-Test'),'disposable');
    b=await createBrowser(name,output);await b.send('Emulation.setFocusEmulationEnabled',{enabled:true});report.version=await b.send('Browser.getVersion');
    const ev=b.evaluate,nav=route=>b.navigate(base+route);
    const run=async(label,fn)=>{try{await fn();report.cases.push({label,status:'PASS'});console.log('PASS '+label);}catch(e){report.cases.push({label,status:'FAIL',error:e.message});throw e;}};
    const submit=selector=>b.navigationAction(()=>b.click(selector+' button[type=submit]'));
    const login=async email=>{await nav('/login');await b.fill({email,password});await submit('[data-auth]');assert.equal(await ev('location.pathname'),'/dashboard/tools');};
    const logout=async()=>{await nav('/dashboard/tools');await submit('[data-logout]');};
    const signature=()=>ev("[...document.querySelectorAll('#main-nav a')].map(x=>({text:x.textContent.trim(),path:new URL(x.href).pathname}))");
    const layout=async label=>{const r=await ev("({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,theme:document.documentElement.dataset.theme,sharedCss:!!document.querySelector('link[href=\"/css/primeskill.css\"]'),header:!!document.querySelector('.ps-nav'),footer:!!document.querySelector('.ps-footer')})");assert.ok(r.scrollWidth<=r.width+1,label+' overflow');assert.ok(r.sharedCss&&r.header&&r.footer);report.layouts.push({label,...r,status:'PASS'});};
    const errorCheck=async(role,route,code)=>{
        await nav('/tools');const normal=await signature();assert.equal(await nav(route),code);assert.deepEqual(await signature(),normal,role+' error navigation mismatch');
        const actions=await ev("[...document.querySelectorAll('.ps-error-page .ps-e-actions a')].map(x=>new URL(x.href).pathname)");
        assert.equal(actions.includes('/login'),role==='ANONYMOUS');assert.equal(actions.includes('/admin/tools'),role==='ADMIN');
        assert.equal(await ev("document.querySelector('[data-logout]')!==null"),role!=='ANONYMOUS');await layout(role+' '+code+' error');
    };
    await run('Anonymous controller and framework errors use signed-out shared layout',async()=>{
        await b.viewport(1440);await errorCheck('ANONYMOUS','/tools/missing-followup',404);await errorCheck('ANONYMOUS','/css/missing-followup.css',404);await b.screenshot('anonymous-error');
    });
    const email='followup-'+Date.now()+'@example.test';await nav('/register');await b.fill({displayName:'Follow-up member',email,password});await submit('[data-auth]');await login(email);
    for(const populated of [false,true])await run('My reviews '+(populated?'populated':'empty')+' uses shared themes and mobile layout',async()=>{
        if(populated)assert.equal(await ev("sessionRequest('/api/v1/tools/3/reviews',{rating:4,comment:'Follow-up review content'}).then(r=>r.status)"),201);
        for(const theme of ['light','dark'])for(const width of [375,1440]){
            await b.viewport(width);await nav('/my/reviews');await ev(`document.documentElement.dataset.theme=${JSON.stringify(theme)}`);await layout('My reviews '+(populated?'populated':'empty')+' '+theme+' '+width);
            assert.equal(await ev("document.querySelectorAll('article.ps-review').length"),populated?1:0);
            assert.ok((await ev('document.body.textContent')).includes(populated?'Follow-up review content':'No reviews yet.'));
            if(width===375)await b.screenshot('my-reviews-'+(populated?'populated':'empty')+'-'+theme);
        }
    });
    await b.viewport(1440);
    await run('USER errors and forbidden admin page retain signed-in shared navigation',async()=>{
        await errorCheck('USER','/tools/missing-followup',404);await errorCheck('USER','/css/missing-followup.css',404);await errorCheck('USER','/admin/tools',403);await b.screenshot('user-forbidden');
    });
    await logout();await login('owner@sprint3.test');
    const original=await ev("fetch('/api/v1/tools/1').then(r=>r.json())");
    for(const edit of [false,true])for(const blank of ['', '   '])await run((edit?'Edit':'Create')+' blank-name '+(blank?'spaces':'empty')+' shows Thai and preserves all fields',async()=>{
        await nav(edit?'/dashboard/tools/1/edit':'/dashboard/tools/new');
        const values={name:blank,slug:edit?original.slug:'blank-followup-'+Date.now(),shortDescription:'Preserve short description',description:'Preserve full description',categoryId:'1',repositoryUrl:'https://example.test/kept'};
        await b.fill(values);assert.equal(await submit('.ps-tool-form form'),200);
        assert.equal(await ev("document.getElementById('name-error').textContent"),'กรุณาระบุชื่อเครื่องมือ');
        assert.equal(await ev("document.activeElement.hasAttribute('data-error-summary')"),true);
        for(const [id,value] of Object.entries(values))assert.equal(await ev(`document.getElementById(${JSON.stringify(id)}).value`),value,id+' lost');
        if(edit)assert.equal(await ev("fetch('/api/v1/tools/1').then(r=>r.json()).then(x=>x.name)"),original.name);
        else assert.equal(await ev(`fetch('/api/v1/tools?q='+${JSON.stringify(values.slug)}).then(r=>r.json()).then(x=>x.totalElements)`),0);
        await layout('Thai validation '+(edit?'edit':'create')+' '+(blank?'spaces':'empty'));await b.screenshot('name-'+(edit?'edit':'create')+'-'+(blank?'spaces':'empty'));
    });
    await logout();await login('admin@sprint3.test');
    await run('ADMIN errors and forbidden owner-only page retain shared admin recovery actions',async()=>{
        await errorCheck('ADMIN','/tools/missing-followup',404);await errorCheck('ADMIN','/css/missing-followup.css',404);await errorCheck('ADMIN','/dashboard/tools/1/versions',403);await b.screenshot('admin-forbidden');
        assert.equal(await nav('/admin/tools'),200);
    });
    assert.deepEqual(b.exceptions,[]);report.status='PASS';
}catch(e){report.status='FAIL';report.error=e.message;console.error(e.stack);process.exitCode=1;}
finally{if(b){report.javascriptExceptions=b.exceptions;report.exceptionEvents=b.exceptionEvents;report.exceptionRevocations=b.exceptionRevocations;try{if(report.status==='FAIL')await b.screenshot('failure');}catch{}b.close();}fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({status:report.status,cases:report.cases.length,layouts:report.layouts.length,output}));}})();
