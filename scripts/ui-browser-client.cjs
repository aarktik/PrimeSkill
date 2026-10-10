'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function createBrowser(browserName, output) {
    const executables = {chrome:'C:/Program Files/Google/Chrome/Application/chrome.exe',
        edge:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'};
    assert.ok(executables[browserName], 'Choose chrome or edge');
    fs.mkdirSync(output, {recursive:true});
    const profile = path.join(output, 'browser-profile');
    const process = spawn(executables[browserName], ['--headless=new','--disable-gpu','--no-first-run',
        '--no-default-browser-check','--remote-debugging-port=0','--window-size=1440,1000',
        `--user-data-dir=${profile}`, 'about:blank'], {windowsHide:true, stdio:'ignore'});
    let socket;
    try {
        let active;
        for(let i=0;i<150;i++) {try {active=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8');break;} catch {} await delay(100);}
        assert.ok(active,'Isolated browser did not start');
        const port = active.split('\n')[0];
        const targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
        const target = targets.find(item => item.type==='page');
        socket = new WebSocket(target.webSocketDebuggerUrl);
        await new Promise((resolve,reject) => {socket.onopen=resolve;socket.onerror=reject;});
        let id=0, documentStatus=0;
        const pending=new Map(), loads=new Set(), exceptions=[], exceptionEvents=[], exceptionRevocations=[];
        socket.onmessage = event => {
            const message=JSON.parse(event.data);
            if(message.method==='Page.lifecycleEvent' && message.params.name==='load') loads.add(message.params.loaderId);
            if(message.method==='Network.responseReceived' && message.params.type==='Document') documentStatus=message.params.response.status;
            if(message.method==='Runtime.exceptionThrown') {
                const details=message.params.exceptionDetails;
                exceptions.push(details);exceptionEvents.push(details);
            }
            if(message.method==='Runtime.exceptionRevoked') {
                exceptionRevocations.push(message.params);
                const history=exceptionEvents.find(x=>x.exceptionId===message.params.exceptionId);
                if(history)history.revoked=message.params.reason;
                const index=exceptions.findIndex(x=>x.exceptionId===message.params.exceptionId);
                if(index>=0)exceptions.splice(index,1);
            }
            if(message.id && pending.has(message.id)) {
                const request=pending.get(message.id);pending.delete(message.id);
                message.error ? request.reject(Error(JSON.stringify(message.error))) : request.resolve(message.result);
            }
        };
        const send = (method,params={}) => new Promise((resolve,reject) => {
            const next=++id;pending.set(next,{resolve,reject});socket.send(JSON.stringify({id:next,method,params}));
        });
        const evaluate = async expression => {
            const result=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});
            if(result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
            return result.result.value;
        };
        const until = async (expression,timeout=20000) => {
            const start=Date.now();
            while(Date.now()-start<timeout) {
                try {if(await evaluate(expression)) return;} catch(error) {
                    if(!/context|Cannot find|destroyed/i.test(error.message)) throw error;
                }
                await delay(100);
            }
            throw Error('Browser wait timed out: '+expression);
        };
        const ready = async () => {await until("document.readyState==='complete'");await evaluate('document.fonts.ready');await delay(280);};
        const navigate = async url => {
            const navigation=await send('Page.navigate',{url});
            assert.ok(!navigation.errorText,navigation.errorText);
            if(navigation.loaderId) {
                const start=Date.now();while(!loads.has(navigation.loaderId)&&Date.now()-start<20000) await delay(80);
                assert.ok(loads.has(navigation.loaderId),'New document did not finish loading: '+url);
            }
            await ready();return documentStatus;
        };
        const click = async selector => evaluate(`(()=>{const element=document.querySelector(${JSON.stringify(selector)});if(!element)throw Error('Missing control: '+${JSON.stringify(selector)});element.focus();element.click();})()`);
        const navigationAction = async action => {
            const origin=await evaluate('performance.timeOrigin');await action();
            await until(`performance.timeOrigin!==${origin}&&document.readyState==='complete'`);await ready();return documentStatus;
        };
        const fill = values => evaluate(`(()=>{const values=${JSON.stringify(values)};for(const [id,value] of Object.entries(values)){const element=document.getElementById(id);if(!element)throw Error('Missing field: '+id);element.value=value;element.dispatchEvent(new Event('input',{bubbles:true}));element.dispatchEvent(new Event('change',{bubbles:true}));}})()`);
        const key = async (name,shift=false) => {
            const code={Tab:9,Escape:27,Enter:13}[name];
            for(const type of ['keyDown','keyUp']) await send('Input.dispatchKeyEvent',{type,key:name,code:name,windowsVirtualKeyCode:code,nativeVirtualKeyCode:code,modifiers:shift?8:0});
        };
        await send('Page.enable');await send('Runtime.enable');await send('Network.enable');
        await send('Page.setLifecycleEventsEnabled',{enabled:true});
        return {send,evaluate,until,navigate,click,fill,key,navigationAction,ready,exceptions,exceptionEvents,exceptionRevocations,targetId:target.id,
            get status(){return documentStatus;},
            viewport:(width,height=1000)=>send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<600}),
            screenshot:async name=>{await delay(200);const result=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(output,name+'.png'),Buffer.from(result.data,'base64'));},
            close:()=>{socket.close();process.kill();}};
    } catch(error) {socket?.close();process.kill();throw error;}
}
module.exports={createBrowser,delay};
