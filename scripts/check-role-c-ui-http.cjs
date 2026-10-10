const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = process.argv[2] || 'http://127.0.0.1:18088';
const parsed = new URL(base);
assert.ok(['127.0.0.1','localhost'].includes(parsed.hostname), 'Disposable loopback server required');
const checks = [];
const prefix = 'c-accept-' + Date.now();
const out = path.resolve(__dirname, '../code/target/role-c-new-ui');
fs.mkdirSync(out,{recursive:true});

class Client {
  constructor(name) { this.name = name; this.cookies = new Map(); }
  async request(method, url, body, expected = 200, csrf = true) {
    const headers = {};
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k,v]) => `${k}=${v}`).join('; ');
    if (method !== 'GET' && csrf && this.token) headers[this.token.headerName] = this.token.token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(base + url, {method, headers, body:body === undefined ? undefined : JSON.stringify(body), redirect:'manual'});
    for (const value of response.headers.getSetCookie()) {
      const first = value.split(';')[0]; const index = first.indexOf('=');
      this.cookies.set(first.slice(0,index), first.slice(index+1));
    }
    const text = await response.text();
    let data; try { data = JSON.parse(text); } catch { data = text; }
    assert.equal(response.status, expected, `${this.name} ${method} ${url}: ${response.status} ${typeof data === 'object' ? data.code + ' ' + data.message : text.slice(0,100)}`);
    return data;
  }
  async csrf() { this.token = await this.request('GET','/api/v1/auth/csrf'); }
  async login(email,password) { await this.csrf(); await this.request('POST','/api/v1/auth/login',{email,password}); await this.csrf(); }
  async register(name) {
    const email = name + '-' + prefix + '@example.test'; const password = 'LocalSmokeOnly123!';
    await this.csrf(); await this.request('POST','/api/v1/auth/register',{email,password,displayName:name},201);
    await this.login(email,password);
  }
}
const check = (name, action) => { action(); checks.push(name); };
const ids = page => page.content.map(tool => tool.id);
const query = values => new URLSearchParams(values).toString();

(async () => {
  const marker = await fetch(base+'/actuator/health');
  assert.equal(marker.headers.get('X-PrimeSkill-UI-Test'),'disposable','Refusing normal preview/shared DB');
  const anonymous = new Client('anonymous'), owner = new Client('owner'), admin = new Client('admin');
  const reviewer = new Client('reviewer'), other = new Client('other');
  await owner.login('owner@sprint3.test','ReviewOnly123!');
  await admin.login('admin@sprint3.test','ReviewOnly123!');
  await reviewer.register('Reviewer'); await other.register('Other');
  checks.push('Actual login/session/CSRF for owner/admin/reviewers');
  const category = await admin.request('POST','/api/v1/admin/categories',{name:prefix,slug:prefix,description:'Disposable C acceptance'},201);
  const alpha = await admin.request('POST','/api/v1/admin/tags',{name:'Alpha '+prefix,slug:'alpha-'+prefix},201);
  const beta = await admin.request('POST','/api/v1/admin/tags',{name:'Beta '+prefix,slug:'beta-'+prefix},201);
  const records = {};
  for (const name of ['High','Tied','Middle','Low','Unrated','Hidden']) {
    const tool = await owner.request('POST','/api/v1/tools',{name:name+' '+prefix,slug:name.toLowerCase()+'-'+prefix,
      shortDescription:'เครื่องมือสำหรับตรวจระบบรวม '+prefix,description:'Disposable local develop data',categoryId:category.id},201);
    records[name] = tool;
    await owner.request('POST',`/api/v1/tools/${tool.id}/tags/${alpha.id}`,undefined,204);
    if (['High','Middle','Hidden'].includes(name)) await owner.request('POST',`/api/v1/tools/${tool.id}/tags/${beta.id}`,undefined,204);
    if (name !== 'Hidden') {
      const pending = await owner.request('POST',`/api/v1/tools/${tool.id}/submit`);
      await admin.request('POST',`/api/v1/admin/tools/${tool.id}/approve`,{expectedReviewRevision:pending.reviewRevision});
    }
  }
  const createReview = (client,name,rating) => client.request('POST',`/api/v1/tools/${records[name].id}/reviews`,{rating,comment:'Local develop C acceptance'},201);
  await createReview(reviewer,'High',5); await createReview(reviewer,'Tied',5);
  await createReview(reviewer,'Middle',5); await createReview(other,'Middle',4); await createReview(reviewer,'Low',3);
  checks.push('Real API create/tag/submit/approve/review flow commits');
  const filters = {q:prefix,category:String(category.id),tags:alpha.slug+','+beta.slug,sort:'rating',size:'2'};
  const first = await anonymous.request('GET','/api/v1/tools?'+query(filters));
  check('Rating + category + ANY tags: no duplicates/count inflation',()=>{
    assert.equal(first.totalElements,5); assert.equal(first.totalPages,3); assert.deepEqual(ids(first),[records.High.id,records.Tied.id]);
  });
  const second = await anonymous.request('GET','/api/v1/tools?'+query({...filters,page:'1'}));
  const last = await anonymous.request('GET','/api/v1/tools?'+query({...filters,page:'2'}));
  check('Stable rating pagination and unrated last',()=>{
    assert.deepEqual(ids(second),[records.Middle.id,records.Low.id]); assert.deepEqual(ids(last),[records.Unrated.id]); assert.equal(last.last,true);
  });
  for (const sort of ['newest','popular','relevance','rating']) {
    const page = await anonymous.request('GET','/api/v1/tools?'+query({category:category.id,tags:alpha.slug,sort}));
    const blank = await anonymous.request('GET','/api/v1/tools?'+query({q:'  ',category:category.id,tags:alpha.slug,sort}));
    check(sort+' accepts missing/blank keyword with category/tag filters',()=>{assert.equal(page.totalElements,5);assert.equal(blank.totalElements,5);});
  }
  const empty = await anonymous.request('GET','/api/v1/tools?'+query({q:'missing-'+prefix,sort:'rating'}));
  check('Empty search response',()=>assert.equal(empty.totalElements,0));
  const html = await anonymous.request('GET','/tools?'+query({...filters,size:'20'}));
  check('Browse renders real average/count and unrated text',()=>{
    assert.ok(html.includes('<strong>4.5</strong>')); assert.ok(html.includes('2 reviews')); assert.ok(html.includes('No reviews yet')); assert.ok(!html.includes('Hidden '+prefix));
  });
  await anonymous.request('GET',`/api/v1/tools/${records.Hidden.id}/tags`,undefined,404);
  await other.request('GET',`/api/v1/tools/${records.Hidden.id}/tags`,undefined,404);
  await owner.request('GET',`/api/v1/tools/${records.Hidden.id}/tags`);
  await admin.request('GET',`/api/v1/tools/${records.Hidden.id}/tags`);
  checks.push('Hidden tag visibility: anonymous/other 404, owner/admin 200');
  const hidden = records.Hidden.id;
  const link = `/api/v1/tools/${hidden}/tags/${beta.id}`;
  await owner.request('DELETE',link,undefined,204); await admin.request('POST',link,undefined,204);
  await admin.request('DELETE',link,undefined,204); await owner.request('POST',link,undefined,204);
  const beforeDenied = await owner.request('GET',`/api/v1/tools/${hidden}`);
  const beforeTags = await owner.request('GET',`/api/v1/tools/${hidden}/tags`);
  const noCsrf = await owner.request('DELETE',link,undefined,403,false);
  const nonOwner = await other.request('DELETE',link,undefined,403);
  assert.equal(nonOwner.code,'ACCESS_DENIED');
  await anonymous.csrf();
  const anonMutation = await anonymous.request('POST',link,undefined,401);
  assert.equal(anonMutation.code,'UNAUTHENTICATED');
  const afterDenied = await owner.request('GET',`/api/v1/tools/${hidden}`);
  const afterTags = await owner.request('GET',`/api/v1/tools/${hidden}/tags`);
  check('Denied actual HTTP requests leave committed tool/tag state unchanged',()=>{
    assert.deepEqual(afterDenied,beforeDenied); assert.deepEqual(afterTags,beforeTags); assert.equal(noCsrf.code,'ACCESS_DENIED');
  });
  const pending = await owner.request('POST',`/api/v1/tools/${hidden}/submit`);
  const rejected = await admin.request('POST',`/api/v1/admin/tools/${hidden}/reject`,{expectedReviewRevision:pending.reviewRevision});
  check('Reject returns Tool to DRAFT without resetting revision',()=>{
    assert.equal(rejected.status,'DRAFT'); assert.equal(rejected.reviewRevision,pending.reviewRevision);
  });
  await owner.request('DELETE',link,undefined,204); await owner.request('POST',link,undefined,204);
  const resubmitted = await owner.request('POST',`/api/v1/tools/${hidden}/submit`);
  const stale = await admin.request('POST',`/api/v1/admin/tools/${hidden}/approve`,{expectedReviewRevision:pending.reviewRevision},409);
  check('Tag edit/resubmit rejects old approval revision',()=>{
    assert.equal(resubmitted.reviewRevision,pending.reviewRevision+1); assert.equal(stale.code,'STALE_REVIEW_REVISION');
  });
  for (const state of ['PENDING','PUBLISHED','DEPRECATED']) {
    if (state==='PUBLISHED') await admin.request('POST',`/api/v1/admin/tools/${hidden}/approve`,{expectedReviewRevision:resubmitted.reviewRevision});
    if (state==='DEPRECATED') await owner.request('POST',`/api/v1/tools/${hidden}/deprecate`);
    const snapshot = await owner.request('GET',`/api/v1/tools/${hidden}`);
    const associations = await owner.request('GET',`/api/v1/tools/${hidden}/tags`);
    for (const actor of [owner,admin,other]) {
      for (const method of ['POST','DELETE']) {
        const error = await actor.request(method,link,undefined,actor===other?403:409);
        assert.equal(error.code,actor===other?'ACCESS_DENIED':'INVALID_STATE_TRANSITION');
      }
    }
    assert.deepEqual(await owner.request('GET',`/api/v1/tools/${hidden}`),snapshot);
    assert.deepEqual(await owner.request('GET',`/api/v1/tools/${hidden}/tags`),associations);
    checks.push(state+' actual-session tag matrix preserves committed state');
  }
  const inUse = await admin.request('DELETE',`/api/v1/admin/tags/${alpha.id}`,undefined,409);
  check('Catalog delete preserves in-use tag',()=>assert.equal(inUse.code,'RESOURCE_CONFLICT'));
  const newReview = await createReview(reviewer,'Unrated',5);
  let page = await anonymous.request('GET','/api/v1/tools?'+query({q:prefix,sort:'rating',size:'20'}));
  check('New review appears in subsequent rating search',()=>assert.deepEqual(ids(page),[records.High.id,records.Tied.id,records.Unrated.id,records.Middle.id,records.Low.id]));
  await reviewer.request('PUT',`/api/v1/tools/${records.Unrated.id}/reviews/${newReview.id}`,{rating:1,comment:'Edited'});
  let reviewHtml = await anonymous.request('GET','/tools?'+query({q:prefix,sort:'rating'}));
  check('Edited review updates Browse score',()=>{assert.ok(reviewHtml.includes('<strong>1.0</strong>'));assert.ok(reviewHtml.includes('1 review'));});
  await reviewer.request('DELETE',`/api/v1/tools/${records.Unrated.id}/reviews/${newReview.id}`,undefined,204);
  page = await anonymous.request('GET','/api/v1/tools?'+query({q:prefix,sort:'rating',size:'20'}));
  check('Deleted review restores unrated-last order',()=>assert.deepEqual(ids(page),[records.High.id,records.Tied.id,records.Middle.id,records.Low.id,records.Unrated.id]));
  for (const parameters of [{page:'-1'},{size:'0'},{size:'101'},{sort:'unknown'}]) {
    const error=await anonymous.request('GET','/api/v1/tools?'+query(parameters),undefined,400);
    assert.equal(error.code,'VALIDATION_FAILED');
  }
  checks.push('Invalid pagination/sort retain 400 validation contract');
  const evidence={sourceKind:'local uncommitted new UI',baselineSha:'fc9421a086ecf7fabb9bd8ca7d246e843e64cff7',baseUrl:base,database:'isolated in-memory H2',status:'PASS',checks,prefix,
    reviewerEmail:'Reviewer-'+prefix+'@example.test',otherEmail:'Other-'+prefix+'@example.test',tagIds:[alpha.id,beta.id],categoryId:category.id,tagSlugs:[alpha.slug,beta.slug],tools:Object.fromEntries(Object.entries(records).map(([name,tool])=>[name,{id:tool.id,slug:tool.slug}]))};
  fs.writeFileSync(path.join(out,'http-check.json'),JSON.stringify(evidence,null,2));
  console.log(JSON.stringify({status:'PASS',checks:checks.length,sourceSha:evidence.sourceSha}));
})().catch(error=>{fs.writeFileSync(path.join(out,'http-failure.txt'),String(error.stack));console.error(error);process.exitCode=1;});
