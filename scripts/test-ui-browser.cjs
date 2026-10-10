'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {createBrowser,delay}=require('./ui-browser-client.cjs');
const browserName=process.argv[2]||'chrome';
const base=process.argv[3]||'http://127.0.0.1:18088';
const parsed=new URL(base);
assert.ok(['127.0.0.1','localhost'].includes(parsed.hostname),'UI writes are allowed only on the disposable loopback test server');
const runId=Date.now().toString(36);
const output=path.resolve(__dirname,'../code/target/ui-acceptance/'+runId+'-'+browserName);
const report={browser:browserName,base,started:new Date().toISOString(),cases:[],layouts:[],limitations:['Android device behavior is emulated; physical Android and Safari are not certified.'],status:'RUNNING'};
let browser;
(async()=>{try {
    let marker;
    for(let i=0;i<80;i++){try{marker=await fetch(base+'/actuator/health');if(marker.ok)break;}catch{}await delay(250);}
    assert.equal(marker?.headers.get('X-PrimeSkill-UI-Test'),'disposable','Refusing to mutate a normal preview or shared database');
    browser=await createBrowser(browserName,output);
    const b=browser,ev=b.evaluate;
    const run=async(name,fn)=>{try{await fn();report.cases.push({name,status:'PASS'});console.log('PASS '+name);}catch(error){report.cases.push({name,status:'FAIL',error:error.message});throw error;}};
    const nav=url=>b.navigate(base+url);
    const submit=selector=>b.navigationAction(()=>b.click(selector+' button[type=submit]'));
    const confirm=async selector=>{
        await b.click(selector+' button[type=submit]');assert.equal(await ev("document.getElementById('ps-confirm').open"),true);
        return b.navigationAction(()=>b.click('[data-confirm-accept]'));
    };
    const api=async url=>ev(`fetch(${JSON.stringify(url)},{credentials:'same-origin'}).then(async r=>({status:r.status,data:r.headers.get('content-type')?.includes('json')?await r.json():null}))`);
    const login=async(email,password='ReviewOnly123!',next='/dashboard/tools')=>{
        await nav('/login?next='+encodeURIComponent(next));await b.fill({email,password});
        await submit('[data-auth]');assert.equal(await ev('location.pathname'),next.split(/[?#]/)[0]);
    };
    const logout=async()=>{await nav('/dashboard/tools');await submit('[data-logout]');assert.equal(await ev('location.pathname'),'/login');};
    const text=()=>ev('document.body.textContent');
    const layout=async label=>{
        const result=await ev(`(()=>{const width=document.documentElement.clientWidth;const selectors='.ps-hero,.ps-hero-search,.ps-preview-card,.ps-tool-hero,.ps-tool-title-row,.ps-tool-category>span,.ps-panel,.ps-tool-card,h1';const violations=[...document.querySelectorAll(selectors)].filter(x=>x.getClientRects().length).flatMap(x=>{const r=x.getBoundingClientRect();return r.left<-.5||r.right>width+.5?[{element:x.className||x.tagName,left:r.left,right:r.right}]:[]});return {width,scrollWidth:document.documentElement.scrollWidth,violations};})()`);
        assert.ok(result.scrollWidth<=result.width+1,label+' page overflow '+JSON.stringify(result));
        assert.deepEqual(result.violations,[],label+' clipped content '+JSON.stringify(result));
        report.layouts.push({label,...result,status:'PASS'});
    };
    let category,tag,tool,slug='ui-browser-'+runId,review;
    const categoryName='C'.repeat(100-runId.length)+runId;
    const longName='A'.repeat(150),memberEmail='member-'+runId+'@ui.test';

    await run('Public discovery, empty search and safe return destinations',async()=>{
        await b.viewport(1440);assert.equal(await nav('/'),200);await layout('Initial Home');
        await b.click('#home-q');assert.equal(await ev("getComputedStyle(document.getElementById('home-q')).outlineStyle"),'none');assert.notEqual(await ev("getComputedStyle(document.querySelector('.ps-hero-search')).boxShadow"),'none');await b.screenshot('search-focus');
        await nav('/tools');await b.click('#q');assert.equal(await ev("getComputedStyle(document.getElementById('q')).outlineStyle"),'none');await b.fill({q:'published'});await submit('#tool-search');assert.ok(await ev("document.querySelectorAll('.ps-tool-card').length>=1"));
        await b.fill({q:'missing-'+runId});await submit('#tool-search');assert.ok((await text()).includes('No tools found.'));
        await nav('/login?next=https%3A%2F%2Fevil.test');assert.equal(await ev("document.querySelector('[data-auth]').dataset.returnTo"),'/dashboard/tools');
    });
    await run('Admin category and tag CRUD, duplicate retention and real constraint messages',async()=>{
        await login('admin@sprint3.test');await nav('/admin/categories/new');
        await b.fill({name:categoryName,slug:'ui-category-'+runId,description:'A test category'});await submit('.ps-reference-form');
        category=(await api('/api/v1/categories')).data.find(x=>x.slug==='ui-category-'+runId).id;
        await nav('/admin/categories/new');await b.fill({name:categoryName,slug:'ui-category-'+runId,description:'Keep this duplicate value'});await submit('.ps-reference-form');
        assert.ok((await text()).includes('already exists'));assert.equal(await ev("document.getElementById('description').value"),'Keep this duplicate value');
        await nav('/admin/categories/'+category+'/edit');await b.fill({description:'Updated using the UI'});await submit('.ps-reference-form');
        await nav('/admin/tags/new');await b.fill({name:'UI tag '+runId,slug:'ui-tag-'+runId});await submit('.ps-reference-form');
        tag=(await api('/api/v1/tags')).data.find(x=>x.slug==='ui-tag-'+runId).id;
        await nav('/admin/tags/'+tag+'/edit');await b.fill({name:'Updated tag '+runId});await submit('.ps-reference-form');
        await nav('/admin/tags/new');await b.fill({name:'n'.repeat(101),slug:'Bad Slug'});assert.equal(await submit('.ps-reference-form'),200);
        assert.ok((await text()).includes('Use at most 100 characters.'));assert.ok((await text()).includes('lowercase letters'));
        assert.equal(await ev("document.activeElement.hasAttribute('data-error-summary')"),true);
        await b.screenshot('catalog-errors');
        for(const kind of ['categories','tags']) {
            const tempSlug='delete-'+kind+'-'+runId;await nav('/admin/'+kind+'/new');
            await b.fill({name:'Temporary '+kind+' '+runId,slug:tempSlug});await submit('.ps-reference-form');
            const temp=(await api('/api/v1/'+kind)).data.find(x=>x.slug===tempSlug);
            await confirm(`form[action="/admin/${kind}/${temp.id}/delete"]`);
            assert.equal((await api('/api/v1/'+kind)).data.some(x=>x.id===temp.id),false);
        }
        await logout();
    });
    await run('Owner draft, pattern and duplicate errors preserve the editor',async()=>{
        await login('owner@sprint3.test');await nav('/dashboard/tools/new');
        await b.fill({name:longName,slug:'Bad Slug',shortDescription:'A useful test tool',description:'Persistent creator details',categoryId:String(category)});
        assert.equal(await submit('.ps-tool-form form'),200);assert.ok((await text()).includes('lowercase letters'));assert.equal(await ev("document.getElementById('description').value"),'Persistent creator details');
        await b.fill({slug});await submit('.ps-tool-form form');assert.equal(await ev('location.pathname'),'/tools/'+slug);
        tool=(await api('/api/v1/tools/'+slug)).data.id;
        await nav('/dashboard/tools/new');await b.fill({name:'Duplicate attempt',slug,shortDescription:'Short',description:'Preserve duplicate attempt',categoryId:String(category)});
        await submit('.ps-tool-form form');assert.ok((await text()).includes('slug is already in use'));assert.equal(await ev("document.getElementById('description').value"),'Preserve duplicate attempt');
        await nav('/dashboard/tools/'+tool+'/edit');await b.fill({description:'Updated description through the native form'});await submit('.ps-tool-form form');
    });
    await run('Owner tag assignment/removal and release CRUD through native forms',async()=>{
        await nav('/dashboard/tools/'+tool+'/tags');await b.fill({tagId:String(tag)});await submit('.ps-reference-form');
        assert.equal((await api('/api/v1/tools/'+tool+'/tags')).data.length,1);
        await submit('.ps-assigned-tag form');assert.equal((await api('/api/v1/tools/'+tool+'/tags')).data.length,0);
        await b.fill({tagId:String(tag)});await submit('.ps-reference-form');
        const versions='/dashboard/tools/'+tool+'/versions';await nav(versions+'/new');await b.fill({version:'1.0.0',releaseNotes:'First release from the UI'});await submit('.ps-e-form');
        let rows=(await api('/api/v1/tools/'+tool+'/versions')).data;const first=rows[0].id;
        await nav(versions+'/'+first+'/edit');await b.fill({releaseNotes:'Edited release notes'});await submit('.ps-e-form');
        await nav(versions+'/new');await b.fill({version:'1.0.0',releaseNotes:'Keep duplicate notes'});await submit('.ps-e-form');
        assert.ok((await text()).includes('already exists'));assert.equal(await ev("document.getElementById('releaseNotes').value"),'Keep duplicate notes');
        await nav(versions+'/new');await b.fill({version:'temporary',releaseNotes:'Delete this test release'});await submit('.ps-e-form');
        rows=(await api('/api/v1/tools/'+tool+'/versions')).data;const temporary=rows.find(x=>x.version==='temporary').id;
        await confirm(`form[action="${versions}/${temporary}/delete"]`);
        assert.equal((await api('/api/v1/tools/'+tool+'/versions')).data.length,1);
        await b.screenshot('owner-releases');
    });
    await run('Confirmation keyboard cancellation does not lock the original form',async()=>{
        await nav('/dashboard/tools/'+tool+'/versions');const selector=`form[action="/dashboard/tools/${tool}/submit"]`;
        await b.click(selector+' button');await b.key('Escape');assert.equal(await ev("document.getElementById('ps-confirm').open"),false);
        assert.equal(await ev(`document.activeElement.matches(${JSON.stringify(selector+' button')})`),true);
        assert.equal(await ev(`document.querySelector(${JSON.stringify(selector+' button')}).disabled`),false);
        await confirm(selector);assert.equal((await api('/api/v1/tools/'+tool)).data.status,'PENDING');await logout();
    });
    await run('Moderation reject, resubmit, stale revision recovery and approval',async()=>{
        await login('admin@sprint3.test');await nav('/admin/tools');
        await confirm(`form[action="/admin/tools/${tool}/reject"]`);assert.equal((await api('/api/v1/tools/'+tool)).data.status,'DRAFT');await logout();
        await login('owner@sprint3.test');await nav('/dashboard/tools/'+tool+'/versions');await confirm(`form[action="/dashboard/tools/${tool}/submit"]`);await logout();
        await login('admin@sprint3.test');await nav('/admin/tools');const approve=`form[action="/admin/tools/${tool}/approve"]`;
        const revision=await ev(`document.querySelector(${JSON.stringify(approve+' input[name=expectedReviewRevision]')}).value`);
        await ev(`document.querySelector(${JSON.stringify(approve+' input[name=expectedReviewRevision]')}).value=String(Number(${JSON.stringify(revision)})-1)`);
        assert.equal(await confirm(approve),409);assert.equal((await api('/api/v1/tools/'+tool)).data.status,'PENDING');
        await nav('/admin/tools');await confirm(approve);assert.equal((await api('/api/v1/tools/'+tool)).data.status,'PUBLISHED');
        await nav('/admin/categories');await confirm(`form[action="/admin/categories/${category}/delete"]`);assert.ok((await text()).includes('still used'));
        await nav('/admin/tags');await confirm(`form[action="/admin/tags/${tag}/delete"]`);assert.ok((await text()).includes('still used'));await logout();
    });
    await run('Registration and login retain the original review destination',async()=>{
        const target='/tools/'+slug+'#reviews-heading';await nav('/register?next='+encodeURIComponent(target));
        await b.fill({displayName:'Browser member',email:memberEmail,password:'UiMember123!'});await submit('[data-auth]');
        assert.equal(await ev('location.pathname'),'/login');assert.equal(await ev("document.querySelector('[data-auth]').dataset.returnTo"),target);
        await b.fill({email:memberEmail,password:'WrongPassword123!'});await b.click('[data-auth] button[type=submit]');
        await b.until("!document.querySelector('[data-form-error]').hidden&&!document.querySelector('[data-auth] button[type=submit]').disabled");
        assert.ok((await text()).includes('email or password'));assert.equal(await ev("document.querySelector('[data-auth] button svg')!==null"),true);
        await b.fill({password:'UiMember123!'});await submit('[data-auth]');assert.equal(await ev('location.pathname'),'/tools/'+slug);
    });
    await run('Profile updates, optional avatar, invalid values and avatar fallback',async()=>{
        await nav('/profile');await b.fill({displayName:'x',bio:'Keep my biography',avatarUrl:'ftp://invalid.test/image'});await submit('.ps-profile-grid form');
        assert.ok((await text()).includes('Use 2 to 100'));assert.equal(await ev("document.getElementById('bio').value"),'Keep my biography');
        await b.fill({displayName:'M'.repeat(100),avatarUrl:''});await submit('.ps-profile-grid form');
        assert.equal((await api('/api/v1/users/me')).data.avatarUrl,null);
        await b.fill({avatarUrl:base+'/img/missing-avatar.png'});await submit('.ps-profile-grid form');await b.until("document.querySelector('[data-avatar-image]').hidden");
        assert.equal(await ev("document.querySelector('[data-avatar-fallback]').hidden"),false);await b.screenshot('profile');
        await b.fill({avatarUrl:''});await submit('.ps-profile-grid form');
        assert.equal(await nav('/admin/tags'),403);
    });
    await run('Member review validation, create, edit and real rating updates',async()=>{
        await nav('/tools/'+slug);await ev("const select=document.getElementById('new-rating');select.add(new Option('Invalid','9'));select.value='9';document.getElementById('new-comment').value='Preserve failed review'");
        await submit('.ps-review-compose');assert.ok((await text()).includes('allowed range'));assert.equal(await ev("document.getElementById('new-comment').value"),'Preserve failed review');
        assert.equal(await ev("document.activeElement.hasAttribute('data-error-summary')"),true);
        await b.fill({'new-rating':'5','new-comment':'Created from the web UI'});await submit('.ps-review-compose');
        review=(await api('/api/v1/tools/'+tool+'/reviews')).data.content[0].id;
        await ev("document.querySelector('#your-review details').open=true");await b.fill({'your-rating':'3','your-comment':'Updated from the web UI'});await submit('#your-review .ps-e-form');
        assert.equal((await api('/api/v1/tools/'+tool+'/reviews')).data.content[0].rating,3);
        await nav('/tools?sort=rating&tags=ui-tag-'+runId);assert.ok((await text()).includes('3.0'));assert.ok((await text()).includes('1 review'));
        await nav('/tools?sort=rating&category='+category+'&tags=ui-tag-'+runId+'&size=1');assert.equal(await ev("document.querySelectorAll('.ps-tool-card').length"),1);
        await logout();
    });
    await run('Administrator review deletion keeps tool context',async()=>{
        await login('admin@sprint3.test',undefined,'/tools/'+slug);await confirm(`form[action="/tools/${tool}/reviews/${review}/delete"]`);
        assert.equal(await ev('location.pathname'),'/tools/'+slug);assert.equal((await api('/api/v1/tools/'+tool+'/reviews')).data.totalElements,0);await logout();
        await login(memberEmail,'UiMember123!','/tools/'+slug);await b.fill({'new-rating':'4','new-comment':'My own deletion test'});await submit('.ps-review-compose');
        await confirm('#your-review form[data-confirm]');assert.equal((await api('/api/v1/tools/'+tool+'/reviews')).data.totalElements,0);
    });
    await run('Responsive long-content layouts, both themes, drawer keyboard and AX isolation',async()=>{
        for(const theme of ['light','dark']) {
            await ev(`localStorage.setItem('primeskill-theme',${JSON.stringify(theme)})`);
            for(const width of [320,375,768,1024,1440]) {
                await b.viewport(width);for(const route of ['/','/tools?sort=rating','/tools/'+slug,'/profile']){await nav(route);assert.equal(await ev('document.documentElement.dataset.theme'),theme);await layout(theme+' '+width+' '+route);}
                if(width===375){await nav('/tools');await b.click('[data-filter-open]');
                    await ev("document.querySelector('[data-filters] button[type=submit]').focus()");await b.key('Tab');assert.equal(await ev("document.activeElement.closest('[data-filters]')!==null"),true);
                    await b.key('Tab',true);assert.equal(await ev("document.activeElement.matches('[data-filters] button[type=submit]')"),true);
                    const ax=await b.send('Accessibility.getFullAXTree');assert.equal(ax.nodes.some(x=>!x.ignored&&x.role?.value==='link'&&x.name?.value==='PrimeSkill'),false);
                    await b.key('Escape');assert.equal(await ev("document.activeElement.hasAttribute('data-filter-open')"),true);assert.equal(await ev("document.querySelector('.ps-nav').inert"),false);
                    await nav('/');await b.screenshot('home-'+theme+'-mobile');await nav('/tools/'+slug);await b.screenshot('detail-'+theme+'-mobile');
                }
            }
        }
        await b.viewport(812,375);await nav('/tools');await layout('Landscape Browse');
        await b.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await nav('/');
        assert.equal(await ev('getComputedStyle(document.documentElement).scrollBehavior'),'auto');assert.equal(await ev("getComputedStyle(document.querySelector('.ps-tool-card')).animationName"),'none');
    });
    await run('Real browser zoom at 200 and 400 percent preserves reflow',async()=>{
        await b.send('Emulation.clearDeviceMetricsOverride');
        const window=await b.send('Browser.getWindowForTarget',{targetId:b.targetId});
        await b.send('Browser.setWindowBounds',{windowId:window.windowId,bounds:{width:1280,height:1000,windowState:'normal'}});
        const settings=browserName==='edge'?'edge://settings/appearance':'chrome://settings/appearance';
        await b.navigate(settings);
        assert.equal(await ev("typeof chrome.settingsPrivate.setDefaultZoom"),'function');
        await ev("new Promise(r=>chrome.settingsPrivate.setDefaultZoom(1,r))");await nav('/');
        const baseline=await ev('({dpr:devicePixelRatio,width:innerWidth})');
        report.nativeZoom=[];
        for(const factor of [2,4]) {
            await b.navigate(settings);await ev(`new Promise(r=>chrome.settingsPrivate.setDefaultZoom(${factor},r))`);
            await nav('/');const actual=await ev('({dpr:devicePixelRatio,width:innerWidth})');
            assert.ok(Math.abs(actual.dpr/baseline.dpr-factor)<.05,'Browser zoom did not actually change device pixel ratio');
            assert.ok(Math.abs(actual.width*factor-baseline.width)<8,'Browser zoom did not actually change CSS viewport');
            await layout('Native browser zoom '+factor*100+' Home');await nav('/tools/'+slug);await layout('Native browser zoom '+factor*100+' detail');
            await nav('/profile');await layout('Native browser zoom '+factor*100+' profile');
            report.nativeZoom.push({factor,baseline,actual});await b.screenshot('zoom-'+factor*100);
        }
        await b.navigate(settings);await ev("new Promise(r=>chrome.settingsPrivate.setDefaultZoom(1,r))");
        await b.viewport(375);await nav('/');
    });
    await run('Partial JS failure and no-JS retain mobile navigation and native profile forms',async()=>{
        await b.viewport(375);await b.send('Network.setBlockedURLs',{urls:['*/js/role-e.js']});await nav('/tools');
        assert.equal(await ev("document.querySelector('.ps-nav').classList.contains('ps-nav--enhanced')"),false);
        assert.ok(await ev("document.querySelector('#main-nav').getClientRects().length>0"));
        await nav('/login');assert.equal(await ev("document.querySelector('[data-auth] button[type=submit]').disabled"),true);assert.equal(await ev("document.querySelector('[data-auth-unavailable]').hidden"),false);
        await b.send('Network.setBlockedURLs',{urls:[]});await b.send('Emulation.setScriptExecutionDisabled',{value:true});await nav('/tools');
        assert.equal(await ev("document.querySelector('[data-tags-fallback]').hidden"),false);assert.ok(await ev("document.querySelector('#main-nav').getClientRects().length>0"));
        await nav('/profile');await b.fill({bio:'Saved without JavaScript'});await submit('.ps-profile-grid form');assert.equal((await api('/api/v1/users/me')).data.bio,'Saved without JavaScript');
        await b.send('Emulation.setScriptExecutionDisabled',{value:false});
    });
    await run('Expired session and CSRF failure give safe recovery',async()=>{
        await b.viewport(1440);await nav('/profile');await ev("document.querySelector('form[action=\"/profile\"] input[name=_csrf]').remove()");
        assert.equal(await submit('.ps-profile-grid form'),403);assert.equal(await ev("document.querySelector('[data-logout]')!==null"),true);await nav('/profile');
        await ev("sessionRequest('/api/v1/auth/logout')");assert.equal(await submit('.ps-profile-grid form'),403);
        await nav('/profile');assert.equal(await ev('location.pathname'),'/login');
    });
    await run('Owner withdraw and restore retain draft workflow guards',async()=>{
        await login('owner@sprint3.test');await nav('/dashboard/tools/'+tool+'/versions');await confirm(`form[action="/dashboard/tools/${tool}/deprecate"]`);
        assert.equal((await api('/api/v1/tools/'+tool)).data.status,'DEPRECATED');
        await submit(`form[action="/dashboard/tools/${tool}/restore"]`);assert.equal((await api('/api/v1/tools/'+tool)).data.status,'DRAFT');await logout();
        await login('admin@sprint3.test');await nav('/admin/tags');await b.screenshot('admin-tags');
        await nav('/tools/'+tool);assert.ok(await ev("document.querySelector('a[href=\"/dashboard/tools/"+tool+"/tags\"]')!==null"));
        await nav('/dashboard/tools/'+tool+'/edit');await b.fill({description:'An admin can edit draft metadata'});await submit('.ps-tool-form form');
        assert.equal((await api('/api/v1/tools/'+tool)).data.description,'An admin can edit draft metadata');
        await confirm(`form[action="/dashboard/tools/${tool}/delete"]`);assert.equal((await api('/api/v1/tools/'+tool)).status,404);
        await nav('/admin/tags');await confirm(`form[action="/admin/tags/${tag}/delete"]`);assert.equal((await api('/api/v1/tags')).data.some(x=>x.id===tag),false);
        await nav('/admin/categories');await confirm(`form[action="/admin/categories/${category}/delete"]`);assert.equal((await api('/api/v1/categories')).data.some(x=>x.id===category),false);
    });
    await run('Workspace and administration layouts cover every existing and added page',async()=>{
        await b.viewport(1440);await nav('/admin/tags');
        const adminRoutes=['/dashboard/tools','/my/reviews','/admin/tools','/admin/categories','/admin/tags','/admin/categories/new','/admin/tags/new'];
        for(const theme of ['light','dark']) {
            await ev(`localStorage.setItem('primeskill-theme',${JSON.stringify(theme)})`);
            for(const width of [320,375,768,1024,1440]) {
                await b.viewport(width);for(const route of adminRoutes){await nav(route);await layout(theme+' admin '+width+' '+route);}
            }
        }
        await b.viewport(1440);await logout();await login('owner@sprint3.test');
        const ownerRoutes=['/dashboard/tools','/dashboard/tools/new','/dashboard/tools/1/edit','/dashboard/tools/1/tags','/dashboard/tools/1/versions','/dashboard/tools/1/versions/new','/tools/preview-published/versions'];
        for(const theme of ['light','dark']) {
            await ev(`localStorage.setItem('primeskill-theme',${JSON.stringify(theme)})`);
            for(const width of [320,375,768,1024,1440]) {
                await b.viewport(width);for(const route of ownerRoutes){await nav(route);await layout(theme+' owner '+width+' '+route);}
            }
        }
        await b.viewport(1440);
    });
    await run('Network failure keeps auth controls usable and gives actionable feedback',async()=>{
        await logout();await nav('/login');await b.fill({email:memberEmail,password:'UiMember123!'});
        await b.send('Network.emulateNetworkConditions',{offline:true,latency:0,downloadThroughput:0,uploadThroughput:0});
        await b.click('[data-auth] button[type=submit]');await b.until("!document.querySelector('[data-form-error]').hidden&&!document.querySelector('[data-auth] button[type=submit]').disabled");
        assert.ok((await text()).includes('Unable to connect'));assert.equal(await ev("document.querySelector('[data-auth] button svg')!==null"),true);
        await b.send('Network.emulateNetworkConditions',{offline:false,latency:0,downloadThroughput:-1,uploadThroughput:-1});
    });
    assert.deepEqual(b.exceptions,[]);report.status='PASS';
} catch(error) {report.status='FAIL';report.error=error.message;console.error(error.stack);process.exitCode=1;}
finally {if(browser){report.javascriptExceptions=browser.exceptions;try{if(report.status==='FAIL')await browser.screenshot('failure');}catch{}browser.close();}fs.mkdirSync(output,{recursive:true});fs.writeFileSync(path.join(output,'report.json'),JSON.stringify(report,null,2));console.log(JSON.stringify({status:report.status,cases:report.cases.length,layouts:report.layouts.length,output}));}})();
