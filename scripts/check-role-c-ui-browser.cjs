'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createBrowser}=require('./ui-browser-client.cjs');
const name=process.argv[2]||'chrome',base=process.argv[3]||'http://127.0.0.1:18088';
assert.ok(['127.0.0.1','localhost'].includes(new URL(base).hostname),'Disposable loopback required');
const dir=path.resolve(__dirname,'../code/target/role-c-new-ui');
const fixture=JSON.parse(fs.readFileSync(path.join(dir,'http-check.json'),'utf8'));
assert.equal(fixture.baseUrl,base,'Seed and browser server must match');
const output=path.join(dir,name+'-'+Date.now());
const report={status:'RUNNING',browser:name,base,baselineSha:fixture.baselineSha,sourceKind:fixture.sourceKind,cases:[],layouts:[]};
let b;
(async()=>{try {
    const marker=await fetch(base+'/actuator/health');assert.equal(marker.headers.get('X-PrimeSkill-UI-Test'),'disposable');
    b=await createBrowser(name,output);await b.send('Emulation.setFocusEmulationEnabled',{enabled:true});
    report.version=await b.send('Browser.getVersion');
    const ev=b.evaluate,nav=route=>b.navigate(base+route);
    const run=async(label,fn)=>{try{await fn();report.cases.push({label,status:'PASS'});console.log('PASS '+label);}catch(e){report.cases.push({label,status:'FAIL',error:e.message});throw e;}};
    const submit=selector=>b.navigationAction(()=>b.click(selector+' button[type=submit]'));
    const confirm=async selector=>{await b.click(selector+' button[type=submit]');return b.navigationAction(()=>b.click('[data-confirm-accept]'));};
    const login=async(email,password='ReviewOnly123!')=>{await nav('/login');await b.fill({email,password});await submit('[data-auth]');assert.equal(await ev('location.pathname'),'/dashboard/tools');};
    const logout=async()=>{await nav('/dashboard/tools');await submit('[data-logout]');};
    const api=route=>ev(`fetch(${JSON.stringify(route)}).then(async r=>({status:r.status,data:await r.json()}))`);
    const cards=()=>ev("[...document.querySelectorAll('.ps-tool-card h3 a')].map(x=>x.textContent)");
    const expected=labels=>labels.map(label=>label+' '+fixture.prefix);
    const filters={q:fixture.prefix,category:String(fixture.categoryId),tags:fixture.tagSlugs.join(','),sort:'rating',size:'2'};
    const url=extra=>'/tools?'+new URLSearchParams({...filters,...extra});
    const assertFilters=async page=>{
        const actual=await ev('Object.fromEntries(new URLSearchParams(location.search))');
        for(const [key,value] of Object.entries(filters))assert.equal(actual[key],value,key+' lost at page '+page);
        assert.equal(Number(actual.page||0),page);
        assert.equal(await ev("document.querySelectorAll('[data-tag-picker] input:checked').length"),2);
    };
    const webPost=(route,body={},csrf=true)=>ev(`(async()=>{const token=await fetch('/api/v1/auth/csrf').then(r=>r.json());const headers={'Content-Type':'application/x-www-form-urlencoded'};if(${csrf})headers[token.headerName]=token.token;const r=await fetch(${JSON.stringify(route)},{method:'POST',headers,body:new URLSearchParams(${JSON.stringify(body)}),redirect:'manual'});return{status:r.status,text:await r.text()};})()`);
    const id=fixture.tools.Hidden.id,tag=fixture.tagIds[1];
    const tagsRoute='/dashboard/tools/'+id+'/tags',versions='/dashboard/tools/'+id+'/versions';
    const snapshot=async()=>({tool:(await api('/api/v1/tools/'+id)).data,tags:(await api('/api/v1/tools/'+id+'/tags')).data});
    const layout=async label=>{const r=await ev('({width:innerWidth,scrollWidth:document.documentElement.scrollWidth})');assert.ok(r.scrollWidth<=r.width+1,label+' overflow');report.layouts.push({label,...r,status:'PASS'});};

    await run('Home search submits the typed keyword to new Browse',async()=>{
        await b.viewport(1440);await nav('/');await b.fill({'home-q':fixture.prefix});await submit('.ps-hero-search');
        assert.equal(await ev("new URLSearchParams(location.search).get('q')"),fixture.prefix);assert.equal((await cards()).length,5);
        assert.ok(!(await cards()).includes('Hidden '+fixture.prefix));
    });
    await run('Category plus two ANY tags and rating sort use actual UI controls',async()=>{
        await nav('/tools?size=2');await b.fill({q:fixture.prefix});await submit('#tool-search');await b.fill({category:String(fixture.categoryId)});
        for(const tagId of fixture.tagIds)await b.click('#filter-tag-'+tagId);
        await b.navigationAction(()=>b.fill({sort:'rating'}));await assertFilters(0);
        assert.deepEqual(await cards(),expected(['High','Tied']));assert.ok((await ev('document.body.textContent')).includes('5 tools to explore'));
    });
    await run('Next/Previous keep all filters and stable tied-score page boundaries',async()=>{
        await b.navigationAction(()=>b.click('.ps-pagination a:last-child'));await assertFilters(1);assert.deepEqual(await cards(),expected(['Middle','Low']));
        await b.screenshot('browse-page-two');await b.navigationAction(()=>b.click('.ps-pagination a:last-child'));await assertFilters(2);
        assert.deepEqual(await cards(),expected(['Unrated']));assert.equal(await ev("document.querySelector('.ps-pagination a:last-child').getAttribute('aria-disabled')"),'true');
        await b.navigationAction(()=>b.click('.ps-pagination a:first-child'));await assertFilters(1);assert.deepEqual(await cards(),expected(['Middle','Low']));
        await b.navigationAction(()=>b.click('.ps-pagination a:first-child'));await assertFilters(0);assert.deepEqual(await cards(),expected(['High','Tied']));
    });
    await run('Real 4.5/two-review summary, id tie-break and unrated-last display',async()=>{
        await nav(url({size:'20'}));assert.deepEqual(await cards(),expected(['High','Tied','Middle','Low','Unrated']));
        const ratings=await ev("[...document.querySelectorAll('.ps-tool-card')].map(x=>x.querySelector('.ps-tool-rating').textContent.trim())");
        assert.match(ratings[2],/4\.5/);assert.match(ratings[2],/2 reviews/);assert.match(ratings[4],/No reviews yet/);
        assert.ok(fixture.tools.High.id<fixture.tools.Tied.id);
    });
    await run('Single tag narrows results; combining tags does not duplicate cards',async()=>{
        await nav(url({tags:fixture.tagSlugs[1],size:'20'}));assert.deepEqual(await cards(),expected(['High','Middle']));
        await nav(url({size:'20'}));assert.equal(new Set(await cards()).size,5);
        await b.navigationAction(()=>b.click('.ps-filter-title a'));assert.equal(await ev('location.search'),'');assert.equal(await ev("document.getElementById('q').value"),'');
    });
    await run('No-results state and clear filters are usable',async()=>{
        await b.fill({q:'missing-'+fixture.prefix});await submit('#tool-search');assert.equal((await cards()).length,0);
        assert.ok((await ev('document.body.textContent')).includes('No tools found.'));assert.equal(await ev("document.querySelector('.ps-pagination')===null"),true);
        await b.navigationAction(()=>b.click('.ps-empty a'));assert.ok((await cards()).length>0);
    });
    await run('Mobile drawer applies category/tag/rating filters through the visible UI',async()=>{
        await b.viewport(375);await nav(url({tags:'',size:'20'}));await b.click('[data-filter-open]');await b.click('#filter-tag-'+fixture.tagIds[1]);
        await submit('[data-filters]');assert.deepEqual(await cards(),expected(['High','Middle']));assert.equal(await ev("document.querySelector('[data-filters]').classList.contains('ps-filter-open')"),false);
        await b.screenshot('browse-mobile-filtered');
    });
    await run('New Browse reflows in both themes and retains muted search focus',async()=>{
        for(const theme of ['light','dark'])for(const width of [320,375,1440]){
            await b.viewport(width);await nav(url({size:'20'}));await ev(`document.documentElement.dataset.theme=${JSON.stringify(theme)}`);
            await b.click('#q');assert.equal(await ev("getComputedStyle(document.getElementById('q')).outlineStyle"),'none');await layout(theme+' Browse '+width);
            if(width===375)await b.screenshot('browse-'+theme+'-375');
        }
        await b.viewport(1440);
    });
    await run('Review create/edit/delete through UI refreshes Browse ratings and order',async()=>{
        await login(fixture.reviewerEmail,'LocalSmokeOnly123!');await nav('/tools/'+fixture.tools.Unrated.slug);
        await b.fill({'new-rating':'5','new-comment':'Role C UI rating refresh'});await submit('.ps-review-compose');await nav(url({size:'20'}));
        assert.deepEqual(await cards(),expected(['High','Tied','Unrated','Middle','Low']));
        await nav('/tools/'+fixture.tools.Unrated.slug);await ev("document.querySelector('#your-review details').open=true");
        await b.fill({'your-rating':'1','your-comment':'Role C edited rating'});await submit('#your-review .ps-e-form');await nav(url({size:'20'}));
        assert.deepEqual(await cards(),expected(['High','Tied','Middle','Low','Unrated']));assert.match(await ev("[...document.querySelectorAll('.ps-tool-rating')].at(-1).textContent"),/1\.0/);
        await nav('/tools/'+fixture.tools.Unrated.slug);await confirm('#your-review form[data-confirm]');await nav(url({size:'20'}));
        assert.match(await ev("[...document.querySelectorAll('.ps-tool-rating')].at(-1).textContent"),/No reviews yet/);await logout();
    });
    await run('Owner DRAFT assign/unassign via UI succeeds; missing CSRF does not mutate',async()=>{
        await login('owner@sprint3.test');await nav(versions);await submit(`form[action="/dashboard/tools/${id}/restore"]`);
        await nav(tagsRoute);await submit(`form[action="${tagsRoute}/${tag}/unassign"]`);assert.equal((await api('/api/v1/tools/'+id+'/tags')).data.length,1);
        await b.fill({tagId:String(tag)});await submit('.ps-reference-form');assert.equal((await api('/api/v1/tools/'+id+'/tags')).data.length,2);
        const before=await snapshot();assert.equal((await webPost(tagsRoute+'/'+tag+'/unassign',{},false)).status,403);assert.deepEqual(await snapshot(),before);await logout();
    });
    await run('Admin can assign/unassign another owner DRAFT using the UI',async()=>{
        await login('admin@sprint3.test');await nav(tagsRoute);await submit(`form[action="${tagsRoute}/${tag}/unassign"]`);
        assert.equal((await api('/api/v1/tools/'+id+'/tags')).data.length,1);await b.fill({tagId:String(tag)});await submit('.ps-reference-form');
        assert.equal((await api('/api/v1/tools/'+id+'/tags')).data.length,2);await b.screenshot('admin-draft-tags');await logout();
    });
    await run('Non-owner DRAFT access and forged assign/unassign are denied',async()=>{
        await login('owner@sprint3.test');const before=await snapshot();await logout();await login(fixture.otherEmail,'LocalSmokeOnly123!');
        assert.equal(await nav(tagsRoute),404);assert.equal((await webPost(tagsRoute+'/assign',{tagId:String(tag)})).status,403);
        assert.equal((await webPost(tagsRoute+'/'+tag+'/unassign')).status,403);await logout();await login('owner@sprint3.test');assert.deepEqual(await snapshot(),before);await logout();
    });
    await run('Anonymous tag management requires login and denies forged mutations',async()=>{
        await nav(tagsRoute);assert.equal(await ev('location.pathname'),'/login');assert.equal((await webPost(tagsRoute+'/assign',{tagId:String(tag)})).status,401);
        assert.equal((await webPost(tagsRoute+'/'+tag+'/unassign')).status,401);
    });
    for(const state of ['PENDING','PUBLISHED','DEPRECATED'])await run(state+' tags read-only; owner/admin 409 and non-owner 403 preserve state',async()=>{
        await login('owner@sprint3.test');
        if(state==='PENDING'){await nav(versions);await confirm(`form[action="/dashboard/tools/${id}/submit"]`);}
        if(state==='PUBLISHED'){await logout();await login('admin@sprint3.test');await nav('/admin/tools');await confirm(`form[action="/admin/tools/${id}/approve"]`);await logout();await login('owner@sprint3.test');}
        if(state==='DEPRECATED'){await nav(versions);await confirm(`form[action="/dashboard/tools/${id}/deprecate"]`);}
        const before=await snapshot();assert.equal(before.tool.status,state);
        for(const actor of ['owner','admin','other']){
            if(actor!=='owner'){await logout();await login(actor==='admin'?'admin@sprint3.test':fixture.otherEmail,actor==='other'?'LocalSmokeOnly123!':'ReviewOnly123!');}
            const status=actor==='other'?(state==='PUBLISHED'?403:404):200;assert.equal(await nav(tagsRoute),status);
            if(actor!=='other'){assert.equal(await ev("document.querySelector('select[name=tagId]')===null"),true);assert.equal(await ev("document.querySelector('.ps-assigned-tag form')===null"),true);}
            const denied=actor==='other'?403:409;
            assert.equal((await webPost(tagsRoute+'/assign',{tagId:String(tag)})).status,denied);
            assert.equal((await webPost(tagsRoute+'/'+tag+'/unassign')).status,denied);
        }
        await logout();await login('owner@sprint3.test');assert.deepEqual(await snapshot(),before);
        await nav(tagsRoute);await b.screenshot('tags-'+state.toLowerCase());await logout();
    });
    assert.deepEqual(b.exceptions,[]);report.javascriptExceptions=b.exceptions;report.status='PASS';
}catch(e){report.status='FAIL';report.error=e.message;console.error(e.stack);process.exitCode=1;}
finally{if(b){report.javascriptExceptions=b.exceptions;report.exceptionEvents=b.exceptionEvents;report.exceptionRevocations=b.exceptionRevocations;try{if(report.status==='FAIL')await b.screenshot('failure');}catch{}b.close();}fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({status:report.status,cases:report.cases.length,layouts:report.layouts.length,output}));}})();
